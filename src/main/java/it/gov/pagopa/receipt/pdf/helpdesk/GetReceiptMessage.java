package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.IoMessageNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
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
public class GetReceiptMessage {

    private final Logger logger = LoggerFactory.getLogger(GetReceiptMessage.class);

    private final ReceiptCosmosService receiptCosmosService;

    public GetReceiptMessage() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
    }

    GetReceiptMessage(ReceiptCosmosService receiptCosmosService) {
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It retrieves the receipt-message with the specified messageId
     * <p>
     *
     * @return response with {@link HttpStatus#OK} and the receipt notification message if found
     */
    @FunctionName("GetReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "GetReceiptMessageTrigger",
                    methods = {HttpMethod.GET},
                    route = "/receipts/io-message/{message-id}",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("message-id") String messageId,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (messageId == null || messageId.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid messageId")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        try {
            IOMessage receipt = this.receiptCosmosService.getReceiptMessage(messageId);
            return request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(receipt)
                    .build();
        } catch (IoMessageNotFoundException e) {
            String responseMsg = String.format("Unable to retrieve the receipt message with messageId %s", messageId);
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
