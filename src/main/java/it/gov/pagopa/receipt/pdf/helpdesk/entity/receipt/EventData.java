package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventData {
    private String payerFiscalCode;
    private String debtorFiscalCode;
    private String transactionCreationDate;
    private String amount;
    private List<CartItem> cart;
}
