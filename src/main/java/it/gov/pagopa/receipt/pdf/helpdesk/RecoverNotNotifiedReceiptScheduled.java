package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.RecoverNotNotifiedReceiptUtils.receiptMassiveRestore;

public class RecoverNotNotifiedReceiptScheduled {

    private final boolean isEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("NOT_NOTIFIED_AUTORECOVER_ENABLED", "true"));

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedReceiptMassive.class);

    private final ReceiptCosmosService receiptCosmosService;

    public RecoverNotNotifiedReceiptScheduled() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
    }

    RecoverNotNotifiedReceiptScheduled(ReceiptCosmosService receiptCosmosService) {
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked on a scheduled basis.
     * <p>
     * It recovers all receipt with the provided status.
     * <p>
     * It recovers the receipt with failed notification ({@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link ReceiptStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link ReceiptStatusType#GENERATED}).
     *
     */
    @FunctionName("RecoverNotNotifiedTimerTriggerProcessor")
    public void processRecoverNotNotifiedScheduledTrigger(
            @TimerTrigger(
                    name = "timerInfo",
                    schedule = "%TRIGGER_GEN_SCHEDULE%"
            )
            String timerInfo,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentReceipts,
            final ExecutionContext context) {

        if (isEnabled) {

            logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

            List<Receipt> receiptList = receiptMassiveRestore(ReceiptStatusType.IO_ERROR_TO_NOTIFY, receiptCosmosService);
            logger.info(String.valueOf(receiptList.size()));
            receiptList.addAll(receiptMassiveRestore(ReceiptStatusType.GENERATED, receiptCosmosService));
            logger.info(String.valueOf(receiptList.size()));

            if (receiptList.isEmpty()) {
                logger.info("[{}] No Receipt to notify", context.getFunctionName());
            }

            documentReceipts.setValue(receiptList);

        }

    }

}
