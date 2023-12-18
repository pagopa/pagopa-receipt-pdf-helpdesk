package it.gov.pagopa.receipt.pdf.helpdesk.utils;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;

import java.util.ArrayList;
import java.util.List;

public class RecoverNotNotifiedReceiptUtils {

    private static final int IO_ERROR_TO_NOTIFY_MASSIVE_RECOVER_MAX_PAGES = Integer.parseInt(System.getenv().getOrDefault("IO_ERROR_TO_NOTIFY_MASSIVE_RECOVER_MAX_PAGES", "2"));

    public static Receipt restoreReceipt(Receipt receipt) {
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

    public static List<Receipt> receiptMassiveRestore(ReceiptStatusType statusType, ReceiptCosmosService receiptCosmosService) {
        List<Receipt> receiptList = new ArrayList<>();
        String continuationToken = null;
        int totalPages = 0;
        do {
            Iterable<FeedResponse<Receipt>> feedResponseIterator =
                    receiptCosmosService.getNotNotifiedReceiptByStatus(continuationToken, 100, statusType);

            for (FeedResponse<Receipt> page : feedResponseIterator) {
                for (Receipt receipt : page.getResults()) {
                    Receipt restoredReceipt = restoreReceipt(receipt);
                    receiptList.add(restoredReceipt);
                }
                continuationToken = page.getContinuationToken();
            }
            totalPages++;
        } while (continuationToken != null && totalPages < IO_ERROR_TO_NOTIFY_MASSIVE_RECOVER_MAX_PAGES);
        return receiptList;
    }

    private RecoverNotNotifiedReceiptUtils() {}
}
