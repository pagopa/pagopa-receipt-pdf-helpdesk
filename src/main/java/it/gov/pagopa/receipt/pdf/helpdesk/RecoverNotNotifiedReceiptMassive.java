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
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.RecoverNotNotifiedReceiptUtils.restoreReceipt;

/**
 * Azure Functions with HTTP Trigger.
 */
public class RecoverNotNotifiedReceiptMassive {

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedReceiptMassive.class);

    private final ReceiptCosmosService receiptCosmosService;

    public RecoverNotNotifiedReceiptMassive() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
    }

    RecoverNotNotifiedReceiptMassive(ReceiptCosmosService receiptCosmosService) {
        this.receiptCosmosService = receiptCosmosService;
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
                    methods = {HttpMethod.POST},
                    route = "receipts/recover-not-notified",
                    authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentReceipts,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        // Get named parameter
        String status = request.getQueryParameters().get("status");
        if (status == null) {
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a status to recover")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        ReceiptStatusType statusType;
        try {
            statusType = ReceiptStatusType.valueOf(status);
        } catch (IllegalArgumentException e) {
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid status to recover")
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
                    this.receiptCosmosService.getNotNotifiedReceiptByStatus(continuationToken, 100, statusType);

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
}
