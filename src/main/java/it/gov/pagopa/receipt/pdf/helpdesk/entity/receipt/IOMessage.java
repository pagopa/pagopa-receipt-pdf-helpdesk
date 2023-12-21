package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IOMessage {

    String messageId;
    String eventId;
}
