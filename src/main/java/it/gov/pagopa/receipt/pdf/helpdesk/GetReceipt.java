package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
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
public class GetReceipt {

    private final Logger logger = LoggerFactory.getLogger(GetReceipt.class);

    private final ReceiptCosmosService receiptCosmosService;

    public GetReceipt() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
    }

    GetReceipt(ReceiptCosmosService receiptCosmosService) {
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
    @FunctionName("GetReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "GetReceiptTrigger",
                    methods = {HttpMethod.GET},
                    route = "receipts/{event-id}",
                    authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("event-id") String eventId,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (eventId == null || eventId.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid biz-event id")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        try {
            Receipt receipt = this.receiptCosmosService.getReceipt(eventId);
            return request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(receipt)
                    .build();
        } catch (ReceiptNotFoundException e) {
            String responseMsg = String.format("Unable to retrieve the receipt with eventId %s", eventId);
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
