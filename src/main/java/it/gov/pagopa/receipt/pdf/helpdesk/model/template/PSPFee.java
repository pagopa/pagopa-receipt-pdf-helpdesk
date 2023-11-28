package it.gov.pagopa.receipt.pdf.helpdesk.model.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class PSPFee {

    private String amount;

}
