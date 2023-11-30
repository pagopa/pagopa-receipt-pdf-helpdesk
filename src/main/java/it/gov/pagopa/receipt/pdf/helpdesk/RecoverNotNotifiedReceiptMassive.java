package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.FeedResponse;
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
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class RecoverNotNotifiedReceiptMassive {

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedReceiptMassive.class);

    private final ReceiptCosmosClient receiptCosmosClient;

    public RecoverNotNotifiedReceiptMassive() {
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    RecoverNotNotifiedReceiptMassive(ReceiptCosmosClient receiptCosmosClient) {
        this.receiptCosmosClient = receiptCosmosClient;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers all receipt with the provided status.
     * <p>
     * It recovers the receipt with failed notification ({@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link ReceiptStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link ReceiptStatusType#GENERATED}).
     *
     * @return response with {@link HttpStatus#OK} if the notification succeeded
     */
    @FunctionName("RecoverNotNotifiedReceiptMassive")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverNotNotifiedMassiveTrigger",
                    methods = {HttpMethod.PUT},
                    route = "/receipts/recover-not-notified?status{status-type}",
                    authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("status-type") ReceiptStatusType statusType,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentReceipts,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (statusType == null) {
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a status to recover")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        if (!statusType.equals(ReceiptStatusType.IO_ERROR_TO_NOTIFY) && !statusType.equals(ReceiptStatusType.GENERATED)) {
            String responseMsg = String.format("The requested status to recover %s is not one of the expected status",
                    statusType);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(responseMsg)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }

        List<Receipt> receiptList = receiptMassiveRestore(statusType);
        if (receiptList.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.OK).body("No receipts restored").build();
        }

        documentReceipts.setValue(receiptList);
        String msg = String.format("Restored %s receipt with success", receiptList.size());
        return request.createResponseBuilder(HttpStatus.OK).body(msg).build();
    }

    private List<Receipt> receiptMassiveRestore(ReceiptStatusType statusType) {
        List<Receipt> receiptList = new ArrayList<>();
        String continuationToken = null;
        do {
            Iterable<FeedResponse<Receipt>> feedResponseIterator =
                    receiptCosmosClient.getNotNotifiedReceiptDocuments(continuationToken, 100, statusType);

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
}
