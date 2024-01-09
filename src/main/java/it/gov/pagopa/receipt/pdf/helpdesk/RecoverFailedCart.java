package it.gov.pagopa.receipt.pdf.helpdesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils.isReceiptStatusValid;

/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedCart {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedCart.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;

    public RecoverFailedCart(){
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
    }

    RecoverFailedCart(BizEventToReceiptService bizEventToReceiptService,
                      CartReceiptsCosmosClient cartReceiptsCosmosClient){
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers the receipt with the specified biz event id that has the following status:
     * - ({@link ReceiptStatusType#INSERTED})
     * - ({@link ReceiptStatusType#FAILED})
     * - ({@link ReceiptStatusType#NOT_QUEUE_SENT})
     * <p>
     * It creates the receipts if not exist and send on queue the event in order to proceed with the receipt generation.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverFailedCart")
    public HttpResponseMessage run (
            @HttpTrigger(name = "RecoverFailedCartTrigger",
                    methods = {HttpMethod.POST},
                    route = "carts/{cart-id}/recover-failed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("cart-id") String cartId,
            @CosmosDBOutput(
                    name = "CartReceiptDatastore",
                    databaseName = "db",
                    collectionName = "cart-for-receipt",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<CartForReceipt> cartForReceiptDocumentdb,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (cartId == null || cartId.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid transaction id")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        try {

            CartForReceipt cartForReceipt = cartReceiptsCosmosClient.getCartItem(cartId);

            if (!cartForReceipt.getStatus().equals(CartStatusType.FAILED) && !cartForReceipt.getStatus().equals(CartStatusType.INSERTED)) {
                String responseMsg = String.format("The requested cart with transaction ID %s is not in the expected status",
                        cartForReceipt.getId());
                return request
                        .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail(responseMsg)
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
            }

            if (cartForReceipt.getTotalNotice() != cartForReceipt.getCartPaymentId().size()) {
                logger.info("[{}] Not all items collected for cart with id {}, this event will be skipped",
                        context.getFunctionName(), cartForReceipt.getId());
                return request
                        .createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.BAD_REQUEST.name())
                                .detail("Items not found on cart for the id")
                                .status(HttpStatus.BAD_REQUEST.value())
                                .build())
                        .build();
            }

            List<BizEvent> bizEventList = this.bizEventToReceiptService.getCartBizEvents(cartForReceipt.getId());
            Receipt receipt = this.bizEventToReceiptService.createCartReceipt(bizEventList);

            if (!isReceiptStatusValid(receipt)) {
                logger.error("[{}] Failed to process cart with id {}: fail to tokenize fiscal codes",
                        context.getFunctionName(), cartForReceipt.getId());
                return request
                        .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail("Failed to process cart: fail to tokenize fiscal codes")
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
            }

            // Add receipt to items to be saved on CosmosDB
            this.bizEventToReceiptService.handleSaveReceipt(receipt);

            if (!isReceiptStatusValid(receipt)) {
                logger.error("[{}] Failed to process cart with id {}: fail to save receipt",
                        context.getFunctionName(), cartForReceipt.getId());
                return request
                        .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail("Failed to process cart: fail to save receipt")
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
            }

            // Send biz event as message to queue (to be processed from the other function)
            this.bizEventToReceiptService.handleSendMessageToQueue(bizEventList, receipt);


            if (!isReceiptStatusValid(receipt)) {
                return request
                        .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail("Failed to process cart: fail to send message queue")
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
            }
            cartForReceipt.setStatus(CartStatusType.SENT);
            logger.info("[{}] Cart with id {} processes successfully. Cart with status: {} and receipt with status: {}",
                    context.getFunctionName(), cartForReceipt.getId(), cartForReceipt.getStatus(), receipt.getStatus());
            cartForReceiptDocumentdb.setValue(cartForReceipt);
            String responseMsg = String.format("Cart with id %s recovered", cartId);
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(responseMsg)
                    .build();

        } catch (CartNotFoundException exception) {
            String msg = String.format("Unable to retrieve the cart with id %s", cartId);
            logger.error(msg, exception);
            return request
                    .createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.NOT_FOUND.name())
                            .detail(msg)
                            .status(HttpStatus.NOT_FOUND.value())
                            .build())
                    .build();
        }

    }
}