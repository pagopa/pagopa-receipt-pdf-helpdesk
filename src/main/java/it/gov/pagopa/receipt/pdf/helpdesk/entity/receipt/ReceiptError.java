package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptErrorStatusType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptError {

    private String id;
    private String bizEventId;
    private String messagePayload;
    private String messageError;
    private ReceiptErrorStatusType status;

}
