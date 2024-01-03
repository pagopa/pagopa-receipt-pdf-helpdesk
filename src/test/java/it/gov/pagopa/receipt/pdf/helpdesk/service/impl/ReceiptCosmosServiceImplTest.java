package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.IoMessageNotFoundException;
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

    @Test
    void getFailedReceiptByStatusSuccessWithStatusFailed() {
        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(receiptCosmosClientMock.getFailedReceiptDocuments(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        Iterable<FeedResponse<Receipt>> response =
                assertDoesNotThrow(() ->
                        sut.getFailedReceiptByStatus("continuation_token", 100, ReceiptStatusType.FAILED));

        assertNotNull(response);

        verify(receiptCosmosClientMock).getFailedReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock, never()).getInsertedReceiptDocuments(anyString(), anyInt());
    }

    @Test
    void getFailedReceiptByStatusSuccessWithStatusNotQueueSent() {
        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(receiptCosmosClientMock.getFailedReceiptDocuments(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        Iterable<FeedResponse<Receipt>> response =
                assertDoesNotThrow(() ->
                        sut.getFailedReceiptByStatus("continuation_token", 100, ReceiptStatusType.NOT_QUEUE_SENT));

        assertNotNull(response);

        verify(receiptCosmosClientMock).getFailedReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock, never()).getInsertedReceiptDocuments(anyString(), anyInt());
    }

    @Test
    void getFailedReceiptByStatusSuccessWithStatusInserted() {
        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(receiptCosmosClientMock.getInsertedReceiptDocuments(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        Iterable<FeedResponse<Receipt>> response =
                assertDoesNotThrow(() ->
                        sut.getFailedReceiptByStatus("continuation_token", 100, ReceiptStatusType.INSERTED));

        assertNotNull(response);

        verify(receiptCosmosClientMock, never()).getFailedReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock).getInsertedReceiptDocuments(anyString(), anyInt());
    }

    @Test
    void getFailedReceiptByStatusFailNullStatus() {
                assertThrows(IllegalArgumentException.class, () ->
                        sut.getFailedReceiptByStatus("continuation_token", 100, null));

        verify(receiptCosmosClientMock, never()).getFailedReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock, never()).getInsertedReceiptDocuments(anyString(), anyInt());
    }

    @Test
    void getFailedReceiptByStatusFailUnexpectedStatus() {
                assertThrows(IllegalStateException.class, () ->
                        sut.getFailedReceiptByStatus("continuation_token", 100, ReceiptStatusType.IO_ERROR_TO_NOTIFY));

        verify(receiptCosmosClientMock, never()).getFailedReceiptDocuments(anyString(), anyInt());
        verify(receiptCosmosClientMock, never()).getInsertedReceiptDocuments(anyString(), anyInt());
    }

    @Test
    void getReceiptMessageSuccess() throws IoMessageNotFoundException {
        when(receiptCosmosClientMock.getIoMessage(anyString())).thenReturn(new IOMessage());

        IOMessage message = assertDoesNotThrow(() -> sut.getReceiptMessage(anyString()));

        assertNotNull(message);
    }

    @Test
    void getReceiptMessageFailClientThrowsReceiptNotFound() throws IoMessageNotFoundException {
        when(receiptCosmosClientMock.getIoMessage(anyString())).thenThrow(IoMessageNotFoundException.class);

        assertThrows(IoMessageNotFoundException.class, () -> sut.getReceiptMessage(anyString()));
    }

    @Test
    void getReceiptMessageFailClientReturnNull() throws IoMessageNotFoundException {
        when(receiptCosmosClientMock.getIoMessage(anyString())).thenReturn(null);

        assertThrows(IoMessageNotFoundException.class, () -> sut.getReceiptMessage(anyString()));
    }


    @Test
    void getCartSuccess() throws CartNotFoundException {
        when(receiptCosmosClientMock.getCartDocument(anyString())).thenReturn(new CartForReceipt());

        CartForReceipt cart = assertDoesNotThrow(() -> sut.getCart(anyString()));

        assertNotNull(cart);
    }

    @Test
    void getCartFailClientThrowsCartNotFoundException() throws CartNotFoundException {
        when(receiptCosmosClientMock.getCartDocument(anyString())).thenThrow(CartNotFoundException.class);

        assertThrows(CartNotFoundException.class, () -> sut.getCart(anyString()));
    }

    @Test
    void getCartFailClientReturnNull() throws CartNotFoundException {
        when(receiptCosmosClientMock.getCartDocument(anyString())).thenReturn(null);

        assertThrows(CartNotFoundException.class, () -> sut.getCart(anyString()));
    }
}