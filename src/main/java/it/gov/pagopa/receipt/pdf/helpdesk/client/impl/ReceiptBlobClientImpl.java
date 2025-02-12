package it.gov.pagopa.receipt.pdf.helpdesk.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.options.BlobDownloadToFileOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BlobStorageClientException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.response.BlobStorageResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Client for the Blob Storage
 */
public class ReceiptBlobClientImpl implements ReceiptBlobClient {

    private final Logger logger = LoggerFactory.getLogger(ReceiptBlobClientImpl.class);
    private static ReceiptBlobClientImpl instance;
    private final String containerName = System.getenv("BLOB_STORAGE_CONTAINER_NAME");
    private static final String FILE_EXTENSION = ".pdf";
    private final BlobServiceClient blobServiceClient;
    private final int downloadTimeout = Integer.parseInt(System.getenv().getOrDefault("BLOB_STORAGE_DOWNLOAD_TIMEOUT", "10"));
    private final int maxRetryDownload = Integer.parseInt(System.getenv().getOrDefault("BLOB_STORAGE_DOWNLOAD_MAX_RETRY", "5"));


    private ReceiptBlobClientImpl() {
        String connectionString = System.getenv("RECEIPTS_STORAGE_CONN_STRING");
        String storageAccount = System.getenv("BLOB_STORAGE_ACCOUNT_ENDPOINT");

        this.blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(storageAccount)
                .connectionString(connectionString)
                .buildClient();
    }

    ReceiptBlobClientImpl(BlobServiceClient serviceClient) {
        this.blobServiceClient = serviceClient;
    }

    public static ReceiptBlobClientImpl getInstance() {
        if (instance == null) {
            instance = new ReceiptBlobClientImpl();
        }

        return instance;
    }

    /**
     * Handles saving the PDF to the blob storage
     *
     * @param pdf      PDF file
     * @param fileName Filename to save the PDF with
     * @return blob storage response with PDF metadata or error message and status
     */
    public BlobStorageResponse savePdfToBlobStorage(InputStream pdf, String fileName) {

        //Create the container and return a container client object
        BlobContainerClient blobContainerClient = this.blobServiceClient.getBlobContainerClient(containerName);
        String fileNamePdf = fileName;
        if (!fileName.endsWith(FILE_EXTENSION)) {
            fileNamePdf = fileName + FILE_EXTENSION;
        }

        //Get a reference to a blob
        BlobClient blobClient = blobContainerClient.getBlobClient(fileNamePdf);

        //Upload the blob
        Response<BlockBlobItem> blockBlobItemResponse = blobClient.uploadWithResponse(
                new BlobParallelUploadOptions(
                        pdf
                ), null, null);

        BlobStorageResponse blobStorageResponse = new BlobStorageResponse();

        //Build response accordingly
        int statusCode = blockBlobItemResponse.getStatusCode();

        if (statusCode == HttpStatus.CREATED.value()) {
            blobStorageResponse.setDocumentName(blobClient.getBlobName());
            blobStorageResponse.setDocumentUrl(blobClient.getBlobUrl());
        }

        blobStorageResponse.setStatusCode(statusCode);

        return blobStorageResponse;
    }

    /**
     * Retrieve a PDF receipt from the blob storage
     *
     * @param fileName file name of the PDF receipt
     * @return the file where the PDF receipt was stored
     */
    public File getAttachmentFromBlobStorage(String fileName) throws BlobStorageClientException {
        BlobContainerClient blobContainerClient = this.blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
        String filePath = createTempDirectory();
        downloadAttachment(fileName, blobClient, filePath);
        return new File(filePath);
    }

    private String createTempDirectory() throws BlobStorageClientException {
        try {
            File workingDirectory = createWorkingDirectory();
            Path tempDirectory = Files.createTempDirectory(workingDirectory.toPath(), "receipt-pdf-service");
            return tempDirectory.toAbsolutePath() + "/receiptPdf.pdf";
        } catch (IOException e) {
            logger.error("Error creating the temp directory to download the PDF receipt from Blob Storage");
            throw new BlobStorageClientException(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(),  e);
        }
    }

    private void downloadAttachment(String fileName, BlobClient blobClient, String filePath) throws BlobStorageClientException {
        try {
            blobClient.downloadToFileWithResponse(
                    getBlobDownloadToFileOptions(filePath),
                    Duration.ofSeconds(downloadTimeout),
                    Context.NONE);
        } catch (UncheckedIOException e) {
            logger.error("I/O error downloading the PDF receipt from Blob Storage");
            throw new BlobStorageClientException(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(),  e);
        } catch (BlobStorageException e) {
            String errMsg;
            if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                errMsg = String.format("PDF receipt with name: %s not found in Blob Storage: %s", fileName, blobClient.getAccountName());
                logger.error(errMsg);
                throw new BlobStorageClientException(e.getStatusCode(), errMsg, e);
            }
            errMsg = String.format("Unable to download the PDF receipt with name: %s from Blob Storage: %s. Error message from server: %s",
                    fileName,
                    blobClient.getAccountName(),
                    e.getServiceMessage()
            );
            logger.error(errMsg);
            throw new BlobStorageClientException(e.getStatusCode(), errMsg, e);
        }
    }

    private BlobDownloadToFileOptions getBlobDownloadToFileOptions(String filePath) {
        return new BlobDownloadToFileOptions(filePath)
                .setDownloadRetryOptions(new DownloadRetryOptions().setMaxRetryRequests(maxRetryDownload))
                .setOpenOptions(new HashSet<>(
                        Arrays.asList(
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.READ
                        ))
                );
    }

    private File createWorkingDirectory() throws IOException {
        File workingDirectory = new File("temp");
        if (!workingDirectory.exists()) {
            Files.createDirectory(workingDirectory.toPath());
        }
        return workingDirectory;
    }
}
