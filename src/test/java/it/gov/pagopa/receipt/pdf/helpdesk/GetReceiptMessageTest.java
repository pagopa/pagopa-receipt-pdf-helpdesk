package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.IoMessageNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.util.HttpResponseMessageMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GetReceiptMessageTest {

    private static final String MESSAGE_ID = "messageId";

    private static final String EVENT_ID = "eventId";

    @Mock
    private ExecutionContext executionContextMock;

    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    private GetReceiptMessage sut;

    private AutoCloseable closeable;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new GetReceiptMessage(receiptCosmosServiceMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void getReceiptReceiptSuccess() throws IoMessageNotFoundException {
        IOMessage receipt = buildMessage();
        when(receiptCosmosServiceMock.getReceiptMessage(MESSAGE_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, MESSAGE_ID, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());
        assertEquals(receipt, response.getBody());
    }

    @Test
    void getReceiptForMissingEventId() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, "", executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());
    }

    @Test
    void getReceiptReceiptNotFound() throws IoMessageNotFoundException {
        when(receiptCosmosServiceMock.getReceiptMessage(MESSAGE_ID)).thenThrow(IoMessageNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, MESSAGE_ID, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());
    }

    private IOMessage buildMessage() {
        return IOMessage.builder()
                .messageId(MESSAGE_ID)
                .eventId(EVENT_ID)
                .build();
    }
}