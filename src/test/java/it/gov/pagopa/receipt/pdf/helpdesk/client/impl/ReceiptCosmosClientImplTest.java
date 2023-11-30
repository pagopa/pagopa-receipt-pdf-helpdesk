package it.gov.pagopa.receipt.pdf.helpdesk.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

class ReceiptCosmosClientImplTest {

    private static final String RECEIPT_ID = "a valid receipt id";

    private CosmosClient mockClient;

    private ReceiptCosmosClientImpl client;

    @BeforeEach
    void setUp() {
        mockClient = mock(CosmosClient.class);

        client = new ReceiptCosmosClientImpl(mockClient);
    }

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_RECEIPT_KEY", mockKey,
                "COSMOS_RECEIPT_SERVICE_ENDPOINT", ""
        ).execute(() -> assertThrows(IllegalArgumentException.class, ReceiptCosmosClientImpl::getInstance));
    }

    @Test
    void runOk() {
        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(receipt);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        Receipt receiptResponse = assertDoesNotThrow(() -> client.getReceiptDocument(RECEIPT_ID));

        assertEquals(RECEIPT_ID, receiptResponse.getId());
    }

    @Test
    void runKo() {
        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);

        when(mockIterator.hasNext()).thenReturn(false);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        assertThrows(ReceiptNotFoundException.class, () -> client.getReceiptDocument("an invalid receipt id"));
    }

    @Test
    void runOk_FailedQueryClient() {
        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(receipt);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        assertDoesNotThrow(() -> client.getFailedReceiptDocuments(null, 100));
    }

    @Test
    void getGeneratedReceiptDocumentsSuccess() {
        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(true);

        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(mockIterator.next()).thenReturn(receipt);

        assertDoesNotThrow(() -> client.getGeneratedReceiptDocuments(null, 100));
    }
    @Test
    void getIOErrorToNotifyReceiptDocumentsSuccess() {
        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(true);

        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(mockIterator.next()).thenReturn(receipt);

        assertDoesNotThrow(() -> client.getIOErrorToNotifyReceiptDocuments(null, 100));
    }
}