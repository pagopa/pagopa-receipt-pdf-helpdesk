package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private int notificationNumRetry;
    @JsonProperty("inserted_at")
    private long insertedAt;
    @JsonProperty("generated_at")
    private long generatedAt;
    @JsonProperty("notified_at")
    private long notifiedAt;
    private boolean isCart;
}
