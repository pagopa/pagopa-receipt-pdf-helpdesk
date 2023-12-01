package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class GetReceiptByOrganizationFiscalCodeAndIUVTest {

    private static final String ORGANIZATION_FISCAL_CODE = "organization fiscal code";
    private static final String IUV = "iuv";
    private static final String EVENT_ID = "eventId";

    @Mock
    private ExecutionContext executionContextMock;

    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;

    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    private GetReceiptByOrganizationFiscalCodeAndIUV sut;

    private AutoCloseable closeable;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new GetReceiptByOrganizationFiscalCodeAndIUV(receiptCosmosServiceMock, bizEventCosmosClientMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void getReceiptReceiptSuccess() throws ReceiptNotFoundException, BizEventNotFoundException {
        BizEvent bizEvent = BizEvent.builder().id(EVENT_ID).build();
        when(bizEventCosmosClientMock.getBizEventDocumentByOrganizationFiscalCodeAndIUV(ORGANIZATION_FISCAL_CODE, IUV))
                .thenReturn(bizEvent);
        Receipt receipt = buildReceipt();
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, ORGANIZATION_FISCAL_CODE, IUV, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());
        assertEquals(receipt, response.getBody());
    }

    @Test
    void getReceiptForMissingOrganizationFiscalCode() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, "", IUV, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());
    }

    @Test
    void getReceiptForMissingIUV() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, ORGANIZATION_FISCAL_CODE, "", executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());
    }

    @Test
    void getReceiptBizEventNotFound() throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocumentByOrganizationFiscalCodeAndIUV(ORGANIZATION_FISCAL_CODE, IUV))
                .thenThrow(BizEventNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, ORGANIZATION_FISCAL_CODE, IUV, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());
    }

    @Test
    void getReceiptReceiptNotFound() throws ReceiptNotFoundException, BizEventNotFoundException {
        BizEvent bizEvent = BizEvent.builder().id(EVENT_ID).build();
        when(bizEventCosmosClientMock.getBizEventDocumentByOrganizationFiscalCodeAndIUV(ORGANIZATION_FISCAL_CODE, IUV))
                .thenReturn(bizEvent);
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, ORGANIZATION_FISCAL_CODE, IUV, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());
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

}