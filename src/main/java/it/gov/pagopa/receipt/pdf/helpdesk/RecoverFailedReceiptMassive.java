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
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils.getEvent;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedReceiptMassive {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceiptMassive.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosService receiptCosmosService;

    public RecoverFailedReceiptMassive(){
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    RecoverFailedReceiptMassive(BizEventToReceiptService bizEventToReceiptService,
                                BizEventCosmosClient bizEventCosmosClient,
                                ReceiptCosmosService receiptCosmosService){
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("RecoverFailedReceiptMassive")
    public HttpResponseMessage run (
            @HttpTrigger(name = "RecoverFailedReceiptMassiveTrigger",
                    methods = {HttpMethod.PUT},
                    route = "/receipts/recover-failed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
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

        List<Receipt> receiptList = new ArrayList<>();
        String continuationToken = null;
        int errorCounter = 0;
        try {
            do {
                Iterable<FeedResponse<Receipt>> feedResponseIterator =
                        this.receiptCosmosService.getFailedReceiptByStatus(continuationToken, 100, statusType);

                for (FeedResponse<Receipt> page : feedResponseIterator) {
                    for (Receipt receipt : page.getResults()) {
                        try {
                            Receipt restored = getEvent(receipt.getEventId(), context, this.bizEventToReceiptService,
                                    this.bizEventCosmosClient, this.receiptCosmosService, receipt, logger);
                            receiptList.add(restored);
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            errorCounter++;
                        }
                    }
                    continuationToken = page.getContinuationToken();
                }
            } while (continuationToken != null);
        } catch (NoSuchElementException e) {
            logger.error(e.getMessage(), e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(e.getMessage())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }
        
        documentdb.setValue(receiptList);
        if (errorCounter > 0) {
            String msg = String.format("Recovered %s receipt but %s encountered an error.", receiptList.size(), errorCounter);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title("Partial OK")
                            .detail(msg)
                            .status(HttpStatus.MULTI_STATUS.value())
                            .build())
                    .build();
        }
        String responseMsg = String.format("Recovered %s receipt", receiptList.size());
        return request.createResponseBuilder(HttpStatus.OK)
                .body(responseMsg)
                .build();
    }
}