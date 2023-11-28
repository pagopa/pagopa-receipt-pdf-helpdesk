package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReceiptMetadata {

    private String name;
    private String url;
}
