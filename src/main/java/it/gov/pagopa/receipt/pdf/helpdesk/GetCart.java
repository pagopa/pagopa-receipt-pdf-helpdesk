package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class GetCart {

    private final Logger logger = LoggerFactory.getLogger(GetCart.class);

    private final ReceiptCosmosService receiptCosmosService;

    public GetCart() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
    }

    GetCart(ReceiptCosmosService receiptCosmosService) {
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It retrieves the receipt with the specified biz event id
     * <p>
     *
     * @return response with {@link HttpStatus#OK} and the receipt if found
     */
    @FunctionName("GetCart")
    public HttpResponseMessage run(
            @HttpTrigger(name = "GetCartTrigger",
                    methods = {HttpMethod.GET},
                    route = "cart/{cart-id}",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("cart-id") String cartId,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (cartId == null || cartId.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid cart id")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        try {
            CartForReceipt cart = this.receiptCosmosService.getCart(cartId);
            return request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(cart)
                    .build();
        } catch (CartNotFoundException e) {
            String responseMsg = String.format("Unable to retrieve the cart with id %s", cartId);
            logger.error("[{}] {}", context.getFunctionName(), responseMsg, e);
            return request
                    .createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.NOT_FOUND.name())
                            .detail(responseMsg)
                            .status(HttpStatus.NOT_FOUND.value())
                            .build())
                    .build();
        }
    }
}
