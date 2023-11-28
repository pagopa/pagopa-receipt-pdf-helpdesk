package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.FeedResponse;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.IONotifyErrorRecoveryRequest;
import it.gov.pagopa.receipt.pdf.helpdesk.util.HttpResponseMessageMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoverNotNotifiedReceiptTest {

    private static final String EVENT_ID = "eventId";

    private final ExecutionContext executionContextMock = mock(ExecutionContext.class);

    @Mock
    private ReceiptCosmosClient receiptCosmosClientMock;

    @Spy
    OutputBinding<List<Receipt>> documentReceipts;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    private RecoverNotNotifiedReceipt sut;

    private AutoCloseable closeable;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new RecoverNotNotifiedReceipt(receiptCosmosClientMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void recoverNotNotifiedReceiptWithEventIdSuccess() throws ReceiptNotFoundException {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(EVENT_ID, false, true);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        Receipt receipt = buildReceipt();
        when(receiptCosmosClientMock.getReceiptDocument(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts).setValue(receiptCaptor.capture());

        assertEquals(1, receiptCaptor.getValue().size());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(0, captured.getNotificationNumRetry());
        assertNull(captured.getReasonErr());
        assertNull(captured.getReasonErrPayer());
    }

    @Test
    void recoverNotNotifiedReceiptWithoutEventIdSuccess() {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(null, false, true);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        FeedResponse feedResponseMock = mock(FeedResponse.class);
        List<Receipt> receiptList = getReceiptList();
        when(feedResponseMock.getResults()).thenReturn(receiptList);
        when(receiptCosmosClientMock.getNotNotifiedReceiptDocuments(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts).setValue(receiptCaptor.capture());

        assertEquals(receiptList.size(), receiptCaptor.getValue().size());
        Receipt captured1 = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured1.getStatus());
        assertEquals(EVENT_ID, captured1.getEventId());
        assertEquals(0, captured1.getNotificationNumRetry());
        assertNull(captured1.getReasonErr());
        assertNull(captured1.getReasonErrPayer());
        Receipt captured2 = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured2.getStatus());
        assertEquals(EVENT_ID, captured2.getEventId());
        assertEquals(0, captured2.getNotificationNumRetry());
        assertNull(captured2.getReasonErr());
        assertNull(captured2.getReasonErrPayer());
    }

    @Test
    void recoverNotNotifiedReceiptWithoutEventIdSuccessWithNoReceiptUpdated() {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(null, false, true);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(feedResponseMock.getResults()).thenReturn(Collections.emptyList());
        when(receiptCosmosClientMock.getNotNotifiedReceiptDocuments(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailForEmptyBody() {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.empty());

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailForInvalidInputParams() {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(EVENT_ID, false, false);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptNotFound() throws ReceiptNotFoundException {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(EVENT_ID, false, true);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        when(receiptCosmosClientMock.getReceiptDocument(EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptFoundIsNull() throws ReceiptNotFoundException {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(EVENT_ID, false, true);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        when(receiptCosmosClientMock.getReceiptDocument(EVENT_ID)).thenReturn(null);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptInGeneratedButOnlyIOErrorToNotify() throws ReceiptNotFoundException {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(EVENT_ID, false, true);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.GENERATED);
        when(receiptCosmosClientMock.getReceiptDocument(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptInIOErrorToNotifyButOnlyGenerated() throws ReceiptNotFoundException {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(EVENT_ID, true, false);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        when(receiptCosmosClientMock.getReceiptDocument(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptInInsertedButOnlyGenerated() throws ReceiptNotFoundException {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(EVENT_ID, true, false);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.INSERTED);
        when(receiptCosmosClientMock.getReceiptDocument(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptInInsertedButOnlyIOErrorToNotify() throws ReceiptNotFoundException {
        IONotifyErrorRecoveryRequest recoveryRequest = new IONotifyErrorRecoveryRequest(EVENT_ID, false, true);

        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<IONotifyErrorRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(recoveryRequest));

        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.INSERTED);
        when(receiptCosmosClientMock.getReceiptDocument(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    private Receipt buildReceipt() {
        return Receipt.builder()
                .eventId(EVENT_ID)
                .status(ReceiptStatusType.IO_ERROR_TO_NOTIFY)
                .reasonErr(ReasonError.builder()
                        .code(500)
                        .message("error message")
                        .build())
                .reasonErrPayer(ReasonError.builder()
                        .code(500)
                        .message("error message")
                        .build())
                .numRetry(0)
                .notificationNumRetry(6)
                .insertedAt(0)
                .generatedAt(0)
                .notifiedAt(0)
                .build();
    }

    private List<Receipt> getReceiptList() {
        List<Receipt> receiptList = new ArrayList<>();
        Receipt receipt1 = buildReceipt();
        Receipt receipt2 = buildReceipt();
        receiptList.add(receipt1);
        receiptList.add(receipt2);
        return receiptList;
    }
}