package it.gov.pagopa.receipt.pdf.helpdesk.utils;

import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;

public class RecoverNotNotifiedReceiptUtils {

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

    private RecoverNotNotifiedReceiptUtils() {}
}
