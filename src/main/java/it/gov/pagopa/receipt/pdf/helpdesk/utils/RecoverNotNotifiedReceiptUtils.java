package it.gov.pagopa.receipt.pdf.helpdesk.utils;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.RecoverNotNotifiedReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RecoverNotNotifiedReceiptUtils {

    private static final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedReceiptUtils.class);

    public static Receipt restoreReceipt(Receipt receipt) {
        receipt.setStatus(ReceiptStatusType.GENERATED);
        receipt.setNotificationNumRetry(0);
        receipt.setNotified_at(0);

        if (receipt.getReasonErr() != null) {
            receipt.setReasonErr(null);
        }
        if (receipt.getReasonErrPayer() != null) {
            receipt.setReasonErrPayer(null);
        }
        return receipt;
    }

    public static List<Receipt> receiptMassiveRestore(ReceiptStatusType statusType, ReceiptCosmosService receiptCosmosService) {

        logger.info("RecoverNotNotified Massive Restore for statusType " + statusType + "executing");

        List<Receipt> receiptList = new ArrayList<>();
        String continuationToken = null;
        do {
            logger.info("RecoverNotNotified Massive Restore executing recover cycle on status " + statusType);

            Iterable<FeedResponse<Receipt>> feedResponseIterator =
                    receiptCosmosService.getNotNotifiedReceiptByStatus(continuationToken, 100, statusType);

            for (FeedResponse<Receipt> page : feedResponseIterator) {
                for (Receipt receipt : page.getResults()) {
                    Receipt restoredReceipt = restoreReceipt(receipt);
                    receiptList.add(restoredReceipt);
                }
                continuationToken = page.getContinuationToken();
                logger.info("RecoverNotNotified Massive Restore continuationToken : " + continuationToken);

            }
        } while (continuationToken != null);

        logger.info("RecoverNotNotified Massive Restore for statusType " + statusType + "executed");


        return receiptList;
    }

    private RecoverNotNotifiedReceiptUtils() {}
}
