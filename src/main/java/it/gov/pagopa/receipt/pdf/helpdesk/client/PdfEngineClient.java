package it.gov.pagopa.receipt.pdf.helpdesk.client;

import it.gov.pagopa.receipt.pdf.helpdesk.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.helpdesk.model.response.PdfEngineResponse;

import java.nio.file.Path;

public interface PdfEngineClient {

    PdfEngineResponse generatePDF(PdfEngineRequest pdfEngineRequest, Path workingDirPath);
}
