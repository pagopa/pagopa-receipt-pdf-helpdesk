package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptCosmosServiceImplTest {

    private ReceiptCosmosClient receiptCosmosClientMock;

    private ReceiptCosmosService sut;

    @BeforeEach
    void setUp() {
        receiptCosmosClientMock = mock(ReceiptCosmosClient.class);

        sut = spy(new ReceiptCosmosServiceImpl(receiptCosmosClientMock));
    }

    @Test
    void getReceiptSuccess() throws ReceiptNotFoundException {
        when(receiptCosmosClientMock.getReceiptDocument(anyString())).thenReturn(new Receipt());

        Receipt receipt = assertDoesNotThrow(() -> sut.getReceipt(anyString()));

        assertNotNull(receipt);
    }

    @Test
    void getReceiptFailClientThrowsReceiptNotFound() throws ReceiptNotFoundException {
        when(receiptCosmosClientMock.getReceiptDocument(anyString())).thenThrow(ReceiptNotFoundException.class);

        assertThrows(ReceiptNotFoundException.class, () -> sut.getReceipt(anyString()));
    }

    @Test
    void getReceiptFailClientReturnNull() throws ReceiptNotFoundException {
        when(receiptCosmosClientMock.getReceiptDocument(anyString())).thenReturn(null);

        assertThrows(ReceiptNotFoundException.class, () -> sut.getReceipt(anyString()));
    }

    @Test
    void getNotNotifiedReceiptByStatusSuccessWithStatusGenerated() {
        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(receiptCosmosClientMock.getGeneratedReceiptDocuments(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        Iterable<FeedResponse<Receipt>> response =
                assertDoesNotThrow(() ->
                        sut.getNotNotifiedReceiptByStatus("continuation_token", 100, ReceiptStatusType.GENERATED));

        assertNotNull(response);

        verify(receiptCosmosClientMock, never()).getIOErrorToNotifyReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock).getGeneratedReceiptDocuments(anyString(), anyInt());
    }

    @Test
    void getNotNotifiedReceiptByStatusSuccessWithStatusIOErrorToNotify() {
        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(receiptCosmosClientMock.getIOErrorToNotifyReceiptDocuments(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        Iterable<FeedResponse<Receipt>> response =
                assertDoesNotThrow(() ->
                        sut.getNotNotifiedReceiptByStatus("continuation_token", 100, ReceiptStatusType.IO_ERROR_TO_NOTIFY));

        assertNotNull(response);

        verify(receiptCosmosClientMock).getIOErrorToNotifyReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock, never()).getGeneratedReceiptDocuments(anyString(), anyInt());
    }

    @Test
    void getNotNotifiedReceiptByStatusFailNullStatus() {
                assertThrows(IllegalArgumentException.class, () ->
                        sut.getNotNotifiedReceiptByStatus("continuation_token", 100, null));

        verify(receiptCosmosClientMock, never()).getIOErrorToNotifyReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock, never()).getGeneratedReceiptDocuments(anyString(), anyInt());
    }

    @Test
    void getNotNotifiedReceiptByStatusFailUnexpectedStatus() {
                assertThrows(IllegalStateException.class, () ->
                        sut.getNotNotifiedReceiptByStatus("continuation_token", 100, ReceiptStatusType.FAILED));

        verify(receiptCosmosClientMock, never()).getIOErrorToNotifyReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock, never()).getGeneratedReceiptDocuments(anyString(), anyInt());
    }
}