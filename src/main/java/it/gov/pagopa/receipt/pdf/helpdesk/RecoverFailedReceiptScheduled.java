package it.gov.pagopa.receipt.pdf.helpdesk;

import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import com.azure.cosmos.models.FeedResponse;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils.getEvent;

/**
 * Azure Functions with Timer trigger.
 */
public class RecoverFailedReceiptScheduled {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceiptScheduled.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosService receiptCosmosService;

    public RecoverFailedReceiptScheduled() {
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    RecoverFailedReceiptScheduled(BizEventToReceiptService bizEventToReceiptService,
                                  BizEventCosmosClient bizEventCosmosClient,
                                  ReceiptCosmosService receiptCosmosService) {
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked periodically according to the specified schedule.
     */
    @FunctionName("RecoverFailedReceiptScheduled")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "%RECOVER_FAILED_CRON%") String timerInfo,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());
        List<Receipt> receiptList = new ArrayList<>();

        receiptList.addAll(recoverFailed(context, ReceiptStatusType.INSERTED));
        receiptList.addAll(recoverFailed(context, ReceiptStatusType.FAILED));
        receiptList.addAll(recoverFailed(context, ReceiptStatusType.NOT_QUEUE_SENT));

        documentdb.setValue(receiptList);
    }

    private List<Receipt> recoverFailed(ExecutionContext context, ReceiptStatusType statusType) {
        try {
            return recover(context, statusType);
        } catch (NoSuchElementException e) {
            logger.error("[{}] Unexpected error during recover of failed receipt for status {}",
                    context.getFunctionName(), statusType, e);
            return Collections.emptyList();
        }
    }

    private List<Receipt> recover(ExecutionContext context, ReceiptStatusType statusType) {
        int errorCounter = 0;
        List<Receipt> receiptList = new ArrayList<>();
        String continuationToken = null;
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
        if (errorCounter > 0) {
            logger.error("[{}] Error recovering {} failed receipt for status {}",
                    context.getFunctionName(), errorCounter, statusType);
        }
        return receiptList;
    }
}