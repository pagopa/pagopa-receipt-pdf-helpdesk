package it.gov.pagopa.receipt.pdf.helpdesk.entity.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {
	private long idTransaction;
	private long grandTotal;
	private long amount;
	private long fee;
	private String transactionStatus;
	private String accountingStatus;
	private String rrn;
	private String authorizationCode;
	private String creationDate;
	private String numAut;
	private String accountCode;
	private TransactionPsp psp;
}
