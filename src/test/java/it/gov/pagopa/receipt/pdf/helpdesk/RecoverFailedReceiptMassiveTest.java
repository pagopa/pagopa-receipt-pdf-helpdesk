package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.ModelBridgeInternal;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.Debtor;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.Payer;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.PaymentInfo;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.Transaction;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.util.HttpResponseMessageMock;
import lombok.SneakyThrows;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoverFailedReceiptMassiveTest {

    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private final String EVENT_ID = "a valid id";

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private BizEventCosmosClientImpl bizEventCosmosClientMock;
    @Mock
    private BizEventToReceiptService bizEventToReceiptServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @Spy
    private OutputBinding<List<Receipt>> documentdb;

    private AutoCloseable closeable;

    private RecoverFailedReceiptMassive sut;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new RecoverFailedReceiptMassive(bizEventToReceiptServiceMock, bizEventCosmosClientMock, receiptCosmosServiceMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void recoverFailedReceiptMassiveSuccess() throws BizEventNotFoundException {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()));

        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), any()))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(createFailedReceipt()),
                                Collections.emptyMap())));

        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(generateValidBizEvent(EVENT_ID));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(receiptCaptor.capture());
        assertEquals(1, receiptCaptor.getValue().size());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailMissingStatusParam() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailInvalidStatusParam() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", "INVALID_STATUS"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassivePartialOK() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()));

        List<Receipt> receiptList = new ArrayList<>();
        receiptList.add(createFailedReceipt());
        Receipt receipt = createFailedReceipt();
        receipt.setEventData(null);
        receiptList.add(receipt);

        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), any()))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(receiptList, Collections.emptyMap())));

        doThrow(PDVTokenizerException.class)
                .when(bizEventToReceiptServiceMock).tokenizeFiscalCodes(any(), any(), any());

        when(bizEventCosmosClientMock.getBizEventDocument(anyString()))
                .thenReturn(generateValidBizEvent(EVENT_ID))
                .thenReturn(generateValidBizEvent("a valid id 2"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.MULTI_STATUS.value(), problemJson.getStatus());
        assertEquals("Partial OK", problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentdb).setValue(receiptCaptor.capture());
        assertEquals(1, receiptCaptor.getValue().size());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailNoSuchElementInIterator() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()));

        Iterable iterableMock = mock(Iterable.class);
        Iterator iteratorMock = mock(Iterator.class);
        when(iterableMock.iterator()).thenReturn(iteratorMock);
        when(iteratorMock.hasNext()).thenThrow(new NoSuchElementException(""));
        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), any()))
                .thenReturn(iterableMock);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemJson.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    private BizEvent generateValidBizEvent(String eventId){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue("AAAAAA00A00A000P");
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue("AAAAAA00A00A000D");

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("1");

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(eventId);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private Receipt createFailedReceipt() {
        Receipt receipt = new Receipt();

        receipt.setId("a valid id");
        receipt.setEventId("a valid id");
        receipt.setVersion("1");

        receipt.setStatus(ReceiptStatusType.FAILED);
        EventData eventData = new EventData();
        eventData.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
        eventData.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
        receipt.setEventData(eventData);

        CartItem item = new CartItem();
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        return receipt;
    }
}