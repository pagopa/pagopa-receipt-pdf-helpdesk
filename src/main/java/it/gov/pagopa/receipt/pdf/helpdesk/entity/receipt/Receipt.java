package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Receipt {

    private String eventId;
    private String id;
    private String version;
    private EventData eventData;
    private IOMessageData ioMessageData;
    private ReceiptStatusType status;
    private ReceiptMetadata mdAttach;
    private ReceiptMetadata mdAttachPayer;
    private int numRetry;
    private ReasonError reasonErr;
    private ReasonError reasonErrPayer;
    private long inserted_at;
    private long generated_at;
    private long notified_at;

}
