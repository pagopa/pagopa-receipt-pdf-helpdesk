package it.gov.pagopa.receipt.pdf.helpdesk.entity.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Details {
	private String blurredNumber;
	private String holder;
	private String circuit; 
}
