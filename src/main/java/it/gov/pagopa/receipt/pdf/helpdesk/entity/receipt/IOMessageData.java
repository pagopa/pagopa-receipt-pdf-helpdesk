package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IOMessageData {
    private String idMessageDebtor;
    private String idMessagePayer;
}
