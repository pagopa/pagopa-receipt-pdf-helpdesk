package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.model.MassiveRecoverCartResult;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils.massiveRecoverCartByStatus;

/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedCartMassive {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedCartMassive.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;

    public RecoverFailedCartMassive() {
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
    }

    RecoverFailedCartMassive(BizEventToReceiptService bizEventToReceiptService,
                             CartReceiptsCosmosClient cartReceiptsCosmosClient) {
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers all the carts with the specified status that has to be one of:
     * - ({@link it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType#INSERTED})
     * - ({@link it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType#FAILED})
     * <p>
     * It attempts to recreate and send receipt data based on cart having a failed or stuck status.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverFailedCartMassive")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverFailedCartMassiveTrigger",
                    methods = {HttpMethod.POST},
                    route = "carts/recover-failed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @CosmosDBOutput(
                    name = "CartReceiptDatastore",
                    databaseName = "db",
                    collectionName = "cart-for-receipt",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<CartForReceipt>> cartForReceiptDocumentdb,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        // Get named parameter
        String status = request.getQueryParameters().get("status");
        if (status == null) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a status to recover")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        CartStatusType statusType;
        try {
            statusType = CartStatusType.valueOf(status);
        } catch (IllegalArgumentException e) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid status to recover")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        MassiveRecoverCartResult recoverResult;
        try {
            recoverResult = massiveRecoverCartByStatus(
                    context, bizEventToReceiptService, cartReceiptsCosmosClient, logger, statusType);
        } catch (Exception e) {
            logger.error("[{}] Unexpected error during recover of failed cart", context.getFunctionName(), e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(e.getMessage())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }
        List<CartForReceipt> cartItems = recoverResult.getCartItems();
        int errorCounter = recoverResult.getErrorCounter();

        cartForReceiptDocumentdb.setValue(cartItems);
        if (errorCounter > 0) {
            String msg = String.format("Recovered %s carts but %s encountered an error.", cartItems.size(), errorCounter);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title("Partial OK")
                            .detail(msg)
                            .status(HttpStatus.MULTI_STATUS.value())
                            .build())
                    .build();
        }
        String responseMsg = String.format("Recovered %s carts", cartItems.size());
        return request.createResponseBuilder(HttpStatus.OK)
                .body(responseMsg)
                .build();
    }

}