package it.gov.pagopa.receipt.pdf.helpdesk.client.impl;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobDownloadToFileOptions;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BlobStorageClientException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.response.BlobStorageResponse;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class ReceiptBlobClientImplTest {

    @Test
    void testSingleton() throws Exception {
        @SuppressWarnings("secrets:S6338")
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "RECEIPTS_STORAGE_CONN_STRING", "DefaultEndpointsProtocol=https;AccountName=samplestorage;AccountKey="+mockKey+";EndpointSuffix=core.windows.net",
                "BLOB_STORAGE_ACCOUNT_ENDPOINT", "https://samplestorage.blob.core.windows.net"
        ).execute(() -> Assertions.assertDoesNotThrow(ReceiptBlobClientImpl::getInstance)
        );
    }

    @Test
    void runOk() {
        BlobServiceClient mockServiceClient = mock(BlobServiceClient.class);
        BlobContainerClient mockContainer = mock(BlobContainerClient.class);
        BlobClient mockClient = mock(BlobClient.class);

        Response mockBlockItem = mock(Response.class);

        when(mockBlockItem.getStatusCode()).thenReturn(HttpStatus.CREATED.value());

        when(mockClient.uploadWithResponse(any(), eq(null), eq(null))).thenReturn(
                mockBlockItem
        );
        String validBlobName = "a valid blob name";
        String validBlobUrl = "a valid blob url";
        when(mockClient.getBlobName()).thenReturn(validBlobName);
        when(mockClient.getBlobUrl()).thenReturn(validBlobUrl);

        when(mockContainer.getBlobClient(any())).thenReturn(mockClient);

        when(mockServiceClient.getBlobContainerClient(any())).thenReturn(mockContainer);

        ReceiptBlobClientImpl receiptBlobClient = new ReceiptBlobClientImpl(mockServiceClient);

        BlobStorageResponse response = receiptBlobClient.savePdfToBlobStorage(InputStream.nullInputStream(), "filename");

        assertEquals(HttpStatus.CREATED.value(), response.getStatusCode());
        assertEquals(validBlobName, response.getDocumentName());
        assertEquals(validBlobUrl, response.getDocumentUrl());

    }

    @Test
    void runKo() {
        BlobServiceClient mockServiceClient = mock(BlobServiceClient.class);
        BlobContainerClient mockContainer = mock(BlobContainerClient.class);
        BlobClient mockClient = mock(BlobClient.class);

        Response mockBlockItem = mock(Response.class);

        when(mockBlockItem.getStatusCode()).thenReturn(HttpStatus.NO_CONTENT.value());

        when(mockClient.uploadWithResponse(any(), eq(null), eq(null))).thenReturn(
                mockBlockItem
        );

        when(mockContainer.getBlobClient(any())).thenReturn(mockClient);

        when(mockServiceClient.getBlobContainerClient(any())).thenReturn(mockContainer);

        ReceiptBlobClientImpl receiptBlobClient = new ReceiptBlobClientImpl(mockServiceClient);

        BlobStorageResponse response = receiptBlobClient.savePdfToBlobStorage(InputStream.nullInputStream(), "filename");

        assertEquals(HttpStatus.NO_CONTENT.value(), response.getStatusCode());
        assertNull(response.getDocumentName());
        assertNull(response.getDocumentUrl());

    }

    @Test
    @SneakyThrows
    void getAttachmentFromBlobStorageSuccess() {
        BlobClient blobClientMock = mock(BlobClient.class);
        doReturn(null).when(blobClientMock)
                .downloadToFileWithResponse(
                        any(BlobDownloadToFileOptions.class),
                        any(Duration.class),
                        any(Context.class)
                );

        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(any())).thenReturn(blobClientMock);

        when(serviceClient.getBlobContainerClient(any())).thenReturn(containerClient);

        ReceiptBlobClientImpl sut = new ReceiptBlobClientImpl(serviceClient);

        File result = sut.getAttachmentFromBlobStorage(anyString());

        assertNotNull(result);
    }

    @Test
    @SneakyThrows
    void getAttachmentFromBlobStorageFailDownloadThrowsUncheckedIOException() {
        BlobClient blobClientMock = mock(BlobClient.class);
        doThrow(UncheckedIOException.class).when(blobClientMock)
                .downloadToFileWithResponse(
                        any(BlobDownloadToFileOptions.class),
                        any(Duration.class),
                        any(Context.class)
                );

        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(any())).thenReturn(blobClientMock);

        when(serviceClient.getBlobContainerClient(any())).thenReturn(containerClient);

        ReceiptBlobClientImpl sut = new ReceiptBlobClientImpl(serviceClient);

        BlobStorageClientException e = assertThrows(BlobStorageClientException.class, () -> sut.getAttachmentFromBlobStorage(anyString()));

        assertNotNull(e);
        assertEquals(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getStatusCode());
    }

    @Test
    @SneakyThrows
    void getAttachmentFromBlobStorageFailDownloadAttachmentNotFound() {
        BlobClient blobClientMock = mock(BlobClient.class);
        HttpResponse responseMock = mock(HttpResponse.class);
        doReturn(404).when(responseMock).getStatusCode();
        doThrow(new BlobStorageException("", responseMock, null)).when(blobClientMock)
                .downloadToFileWithResponse(
                        any(BlobDownloadToFileOptions.class),
                        any(Duration.class),
                        any(Context.class)
                );

        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(any())).thenReturn(blobClientMock);

        when(serviceClient.getBlobContainerClient(any())).thenReturn(containerClient);

        ReceiptBlobClientImpl sut = new ReceiptBlobClientImpl(serviceClient);

        BlobStorageClientException e = assertThrows(BlobStorageClientException.class, () -> sut.getAttachmentFromBlobStorage(anyString()));

        assertNotNull(e);
        assertEquals(HttpStatus.NOT_FOUND.value(), e.getStatusCode());
    }

    @Test
    @SneakyThrows
    void getAttachmentFromBlobStorageFailDownloadThrowsBlobStorageException() {
        BlobClient blobClientMock = mock(BlobClient.class);
        HttpResponse responseMock = mock(HttpResponse.class);
        doReturn(500).when(responseMock).getStatusCode();
        doThrow(new BlobStorageException("", responseMock, null)).when(blobClientMock)
                .downloadToFileWithResponse(
                        any(BlobDownloadToFileOptions.class),
                        any(Duration.class),
                        any(Context.class)
                );

        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(any())).thenReturn(blobClientMock);

        when(serviceClient.getBlobContainerClient(any())).thenReturn(containerClient);

        ReceiptBlobClientImpl sut = new ReceiptBlobClientImpl(serviceClient);

        BlobStorageClientException e = assertThrows(BlobStorageClientException.class, () -> sut.getAttachmentFromBlobStorage(anyString()));

        assertNotNull(e);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getStatusCode());
    }

}