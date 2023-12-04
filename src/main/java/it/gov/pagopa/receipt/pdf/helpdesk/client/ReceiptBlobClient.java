package it.gov.pagopa.receipt.pdf.helpdesk.client;

import it.gov.pagopa.receipt.pdf.helpdesk.exception.BlobStorageClientException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.response.BlobStorageResponse;

import java.io.File;
import java.io.InputStream;

public interface ReceiptBlobClient {

    BlobStorageResponse savePdfToBlobStorage(InputStream pdf, String fileName);
    File getAttachmentFromBlobStorage(String fileName) throws BlobStorageClientException;
}
