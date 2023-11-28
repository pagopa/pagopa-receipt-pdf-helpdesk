package it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReasonError {
    private int code;
    private String message;

}
