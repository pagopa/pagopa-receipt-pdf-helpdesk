package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.RecoverNotNotifiedReceiptUtils.restoreReceipt;

/**
 * Azure Functions with HTTP Trigger.
 */
public class RecoverNotNotifiedReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedReceipt.class);

    private final ReceiptCosmosService receiptCosmosService;

    public RecoverNotNotifiedReceipt() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
    }

    RecoverNotNotifiedReceipt(ReceiptCosmosService receiptCosmosService) {
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers the receipt with the specified biz event id
     * <p>
     * It recovers the receipt with failed notification ({@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link ReceiptStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link ReceiptStatusType#GENERATED}).
     *
     * @return response with {@link HttpStatus#OK} if the notification succeeded
     */
    @FunctionName("RecoverNotNotifiedReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverNotNotifiedTrigger",
                    methods = {HttpMethod.POST},
                    route = "receipts/{event-id}/recover-not-notified",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("event-id") String eventId,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentReceipts,
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

        Receipt receipt;
        try {
            receipt = this.receiptCosmosService.getReceipt(eventId);
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

        if (!receipt.getStatus().equals(ReceiptStatusType.IO_ERROR_TO_NOTIFY) && !receipt.getStatus().equals(ReceiptStatusType.GENERATED)) {
            String responseMsg = String.format("The requested receipt with eventId %s is not in the expected status",
                    receipt.getEventId());
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(responseMsg)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        Receipt restoredReceipt = restoreReceipt(receipt);

        documentReceipts.setValue(Collections.singletonList(restoredReceipt));
        String responseMsg = String.format("Receipt with id %s and eventId %s restored in status %s with success",
                receipt.getId(), receipt.getEventId(), ReceiptStatusType.GENERATED);
        return request.createResponseBuilder(HttpStatus.OK).body(responseMsg).build();
    }
}
