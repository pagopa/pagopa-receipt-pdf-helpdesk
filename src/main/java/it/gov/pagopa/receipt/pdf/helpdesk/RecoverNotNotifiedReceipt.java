package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.FeedResponse;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.NotNotifiedRecoveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class RecoverNotNotifiedReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedReceipt.class);

    private final ReceiptCosmosClient receiptCosmosClient;

    public RecoverNotNotifiedReceipt() {
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    RecoverNotNotifiedReceipt(ReceiptCosmosClient receiptCosmosClient) {
        this.receiptCosmosClient = receiptCosmosClient;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers the receipt with failed notification ({@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link ReceiptStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link ReceiptStatusType#GENERATED}).
     * <p>
     * If invoked with a specific eventId it restore the associated receipt, otherwise it restore all receipt with status
     * {@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}.
     *
     * @return response with {@link HttpStatus#OK} if the notification succeeded
     */
    @FunctionName("RecoverNotNotifiedReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverNotNotifiedTrigger",
                    methods = {HttpMethod.PUT},
                    route = "recoverNotNotified",
                    authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<NotNotifiedRecoveryRequest>> request,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentReceipts,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        Optional<NotNotifiedRecoveryRequest> recoveryRequestOptional = request.getBody();
        if (recoveryRequestOptional.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a valid body").build();
        }
        NotNotifiedRecoveryRequest recoveryRequest = recoveryRequestOptional.get();

        List<ReceiptStatusType> statusToRestore = getStatusToRestore(recoveryRequest);
        if (statusToRestore.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please select at least one status to recover").build();
        }

        String eventId = recoveryRequest.getEventId();
        if (eventId != null) {
            Receipt receipt;
            try {
                receipt = getReceipt(eventId);
            } catch (ReceiptNotFoundException e) {
                String responseMsg = String.format("Unable to retrieve the receipt with eventId %s", eventId);
                logger.error("[{}] {}", context.getFunctionName(), responseMsg, e);
                return request.createResponseBuilder(HttpStatus.NOT_FOUND).body(responseMsg).build();
            }

            if (!statusToRestore.contains(receipt.getStatus())) {
                String responseMsg = String.format("The requested receipt with eventId %s is not in the expected status",
                        receipt.getEventId());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(responseMsg).build();
            }

            Receipt restoredReceipt = restoreReceipt(receipt);

            documentReceipts.setValue(Collections.singletonList(restoredReceipt));
            String responseMsg = String.format("Receipt with id %s and eventId %s restored in status %s with success",
                    receipt.getId(), receipt.getEventId(), ReceiptStatusType.GENERATED);
            return request.createResponseBuilder(HttpStatus.OK).body(responseMsg).build();
        }

        List<Receipt> receiptList = receiptMassiveRestore(recoveryRequest);
        if (receiptList.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.OK).body("No receipts restored").build();
        }

        documentReceipts.setValue(receiptList);
        String msg = String.format("Restored %s receipt with success", receiptList.size());
        return request.createResponseBuilder(HttpStatus.OK).body(msg).build();
    }

    private List<ReceiptStatusType> getStatusToRestore(NotNotifiedRecoveryRequest recoveryRequest) {
        List<ReceiptStatusType> statusToRestore = new ArrayList<>();
        if (recoveryRequest.isGeneratedStatus()) {
            statusToRestore.add(ReceiptStatusType.GENERATED);
        }
        if (recoveryRequest.isIoErrorToNotifyStatus()) {
            statusToRestore.add(ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        }
        return statusToRestore;
    }

    private List<Receipt> receiptMassiveRestore(NotNotifiedRecoveryRequest recoveryRequest) {
        List<Receipt> receiptList = new ArrayList<>();
        String continuationToken = null;
        do {
            Iterable<FeedResponse<Receipt>> feedResponseIterator =
                    receiptCosmosClient
                            .getNotNotifiedReceiptDocuments(
                                    continuationToken,
                                    100,
                                    recoveryRequest.isIoErrorToNotifyStatus(),
                                    recoveryRequest.isGeneratedStatus()
                            );

            for (FeedResponse<Receipt> page : feedResponseIterator) {
                for (Receipt receipt : page.getResults()) {
                    Receipt restoredReceipt = restoreReceipt(receipt);
                    receiptList.add(restoredReceipt);
                }
                continuationToken = page.getContinuationToken();
            }
        } while (continuationToken != null);
        return receiptList;
    }

    private Receipt restoreReceipt(Receipt receipt) {
        receipt.setStatus(ReceiptStatusType.GENERATED);
        receipt.setNotificationNumRetry(0);
        receipt.setNotifiedAt(0);

        if (receipt.getReasonErr() != null) {
            receipt.setReasonErr(null);
        }
        if (receipt.getReasonErrPayer() != null) {
            receipt.setReasonErrPayer(null);
        }
        return receipt;
    }

    //Retrieve receipt from CosmosDB
    private Receipt getReceipt(String eventId) throws ReceiptNotFoundException {
        Receipt receipt;
        try {
            receipt = receiptCosmosClient.getReceiptDocument(eventId);
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("Receipt not found with the biz-event id %s", eventId);
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = String.format("Receipt retrieved with the biz-event id %s is null", eventId);
            throw new ReceiptNotFoundException(errorMsg);
        }
        return receipt;
    }
}
