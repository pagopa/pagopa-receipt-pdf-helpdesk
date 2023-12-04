package it.gov.pagopa.receipt.pdf.helpdesk.exception;

import lombok.Getter;

/**
 * Thrown in case of error when retrieving the PDF receipt from blob storage
 */
@Getter
public class BlobStorageClientException extends Exception {

    private final int statusCode;

    /**
     * Constructs new exception with provided error code, message and cause
     *
     * @param statusCode Error code
     * @param message   Detail message
     * @param cause     Exception causing the constructed one
     */
    public BlobStorageClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
