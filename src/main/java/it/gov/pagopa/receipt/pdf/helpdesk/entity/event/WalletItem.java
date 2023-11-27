package it.gov.pagopa.receipt.pdf.helpdesk.entity.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.WalletType;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletItem {
	private String idWallet;
	private WalletType walletType;
	private List<String> enableableFunctions;
	private boolean pagoPa;
	private String onboardingChannel;
	private boolean favourite;
	private String createDate;
	private Info info;
	private AuthRequest authRequest;
}
