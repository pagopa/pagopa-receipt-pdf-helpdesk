package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
}