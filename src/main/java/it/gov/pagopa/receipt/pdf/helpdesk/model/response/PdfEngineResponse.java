package it.gov.pagopa.receipt.pdf.helpdesk.model.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model class for PDF Engine client's response
 */
@Getter
@Setter
@NoArgsConstructor
public class PdfEngineResponse {

    String tempPdfPath;
    int statusCode;
    String errorMessage;

}
