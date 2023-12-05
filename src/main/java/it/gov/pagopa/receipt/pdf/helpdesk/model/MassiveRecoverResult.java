package it.gov.pagopa.receipt.pdf.helpdesk.model;

import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MassiveRecoverResult {

    private List<Receipt> receiptList;
    private int errorCounter;
}
