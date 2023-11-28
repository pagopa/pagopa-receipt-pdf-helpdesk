package it.gov.pagopa.receipt.pdf.helpdesk.model.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class Transaction {

    private String id;
    private String timestamp;
    private String amount;
    private PSP psp;
    private String rrn;
    private String authCode;
    private PaymentMethod paymentMethod;
    private boolean requestedByDebtor;
    private boolean processedByPagoPA;
}
