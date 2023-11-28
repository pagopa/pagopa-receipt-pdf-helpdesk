package it.gov.pagopa.receipt.pdf.helpdesk.exception;


public class PdfJsonMappingException extends RuntimeException {
    public PdfJsonMappingException(Exception e) {
        super(e);
    }
}
