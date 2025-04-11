package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.UserType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IOMessage {

    String id;
    String messageId;
    String eventId;
    UserType userType;
}
