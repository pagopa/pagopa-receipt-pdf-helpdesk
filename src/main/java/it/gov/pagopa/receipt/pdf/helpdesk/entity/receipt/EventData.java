package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventData {
    private String payerFiscalCode;
    private String debtorFiscalCode;
    private String transactionCreationDate;
    private String amount;
    private List<CartItem> cart;
}
