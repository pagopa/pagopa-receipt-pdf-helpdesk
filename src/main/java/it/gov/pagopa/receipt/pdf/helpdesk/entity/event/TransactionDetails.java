package it.gov.pagopa.receipt.pdf.helpdesk.entity.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionDetails {
	private String origin;
	private User user;
	private Transaction transaction;
	private WalletItem wallet;
}
