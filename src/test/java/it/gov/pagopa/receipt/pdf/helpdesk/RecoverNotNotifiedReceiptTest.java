package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
    private ReceiptCosmosService receiptCosmosServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Spy
    private OutputBinding<List<Receipt>> documentReceipts;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    private RecoverNotNotifiedReceipt sut;

    private AutoCloseable closeable;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new RecoverNotNotifiedReceipt(receiptCosmosServiceMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void recoverNotNotifiedReceiptSuccess() throws ReceiptNotFoundException {
        Receipt receipt = buildReceipt();
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

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
    void recoverNotNotifiedCartReceiptSuccess() throws ReceiptNotFoundException, CartNotFoundException {
        Receipt receipt = buildReceipt();
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

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
    void recoverReceiptFailForMissingEventId() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, "", documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptNotFound() throws ReceiptNotFoundException {
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptInInsertedButOnlyGenerated() throws ReceiptNotFoundException {
        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.INSERTED);
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemJson.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptInInsertedButOnlyIOErrorToNotify() throws ReceiptNotFoundException {
        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.INSERTED);
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemJson.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

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

    private CartForReceipt generateCart() {
        CartForReceipt cart = new CartForReceipt();
        cart.setId("1");
        cart.setStatus(CartStatusType.FAILED);
        cart.setTotalNotice(1);
        cart.setCartPaymentId(new HashSet<>(new ArrayList<>(
                List.of(new String[]{"eventId"}))));
        return cart;
    }
}