package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.helpdesk.util.HttpResponseMessageMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetReceiptErrorTest {

    String INVALID_DATA_BASE64 = "==eyJkYXRhIjoidGVzdCJ9";

    String VALID_DATA_BASE64 = "eyJkYXRhIjoidGVzdCJ9";
    String VALID_DATA = "{\"data\":\"test\"}";

    private ReceiptCosmosClient receiptCosmosClient;
    private ExecutionContext executionContextMock;
    private GetReceiptError sut;

    @BeforeEach
    void setUp() {
        receiptCosmosClient = mock(ReceiptCosmosClient.class);
        executionContextMock = mock(ExecutionContext.class);

        sut = spy(new GetReceiptError(receiptCosmosClient));
    }

    @Test
    void shouldReturnPlainDataOnValidInput() throws ReceiptNotFoundException {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        when(receiptCosmosClient.getReceiptError(Mockito.eq("1"))).thenReturn(getValidBase64ReceiptError());

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, "1", executionContextMock);



        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(ReceiptError.class, response.getBody().getClass());
        assertEquals(VALID_DATA, ((ReceiptError) response.getBody()).getMessagePayload());

    }

    @Test
    void shouldReturnPlainDataOnValidInputWithUnableToDecodeData() throws ReceiptNotFoundException {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        when(receiptCosmosClient.getReceiptError(Mockito.eq("1"))).thenReturn(getInvalidBase64ReceiptError());

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, "1", executionContextMock);


        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(ReceiptError.class, response.getBody().getClass());
        assertEquals(INVALID_DATA_BASE64, ((ReceiptError) response.getBody()).getMessagePayload());

    }

    @Test
    void shouldReturnMissingReceiptErrorMessageOnNonExistingErrorinCosmos() throws ReceiptNotFoundException {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        when(receiptCosmosClient.getReceiptError(Mockito.eq("1"))).thenThrow(new ReceiptNotFoundException("No Error"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, "1", executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(ProblemJson.class, response.getBody().getClass());

        ProblemJson responseData = (ProblemJson) response.getBody();
        assertEquals(HttpStatus.NOT_FOUND.value(), responseData.getStatus());
        assertEquals("No Receipt Error to process on bizEvent with id 1", responseData.getDetail());

    }

    @Test
    void shouldReturnServerErrorMessageOnUnexpectedExceptioninCosmos() throws ReceiptNotFoundException {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer(invocationOnMock -> {
            throw new Exception("Unexpected Error");
        }).when(receiptCosmosClient).getReceiptError(Mockito.eq("1"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, "1", executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertEquals(ProblemJson.class, response.getBody().getClass());

        ProblemJson responseData = (ProblemJson) response.getBody();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), responseData.getStatus());
        assertEquals("Unexpected Error", responseData.getDetail());

    }

    @Test
    void shouldReturnErrorOnMissingRequestData() {
        @SuppressWarnings("unchecked")
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(request, "", executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertEquals(ProblemJson.class, response.getBody().getClass());

        ProblemJson responseData = (ProblemJson) response.getBody();
        assertEquals(HttpStatus.BAD_REQUEST.value(), responseData.getStatus());
        assertEquals("Missing valid search parameter", responseData.getDetail());

    }

    public ReceiptError getValidBase64ReceiptError() {
        return ReceiptError.builder()
                .id("1")
                .bizEventId("1")
                .messageError("test")
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .messagePayload(VALID_DATA_BASE64)
                .build();
    }

    public ReceiptError getInvalidBase64ReceiptError() {
        return ReceiptError.builder()
                .id("1")
                .bizEventId("1")
                .messageError("test")
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .messagePayload(INVALID_DATA_BASE64)
                .build();
    }

}
