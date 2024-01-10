package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.ModelBridgeInternal;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.helpdesk.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.*;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReasonError;
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
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecoverFailedCartMassiveTest {

    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private final String EVENT_ID = "a valid id";

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private CartReceiptsCosmosClient cartReceiptsCosmosClientMock;
    @Mock
    private BizEventCosmosClientImpl bizEventCosmosClientMock;
    @Mock
    private BizEventToReceiptService bizEventToReceiptServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Captor
    private ArgumentCaptor<List<CartForReceipt>> cartForReceiptCaptor;

    @Spy
    private OutputBinding<List<CartForReceipt>> documentdb;

    private AutoCloseable closeable;

    private RecoverFailedCartMassive sut;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new RecoverFailedCartMassive(bizEventToReceiptServiceMock, cartReceiptsCosmosClientMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void recoverFailedReceiptMassiveSuccess() throws BizEventNotFoundException {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", CartStatusType.FAILED.name()));

        when(cartReceiptsCosmosClientMock.getFailedCarts(any(), eq(100)))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(generateCart()),
                                Collections.emptyMap())));

        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(generateValidBizEvent(EVENT_ID));
        when(bizEventToReceiptServiceMock.createCartReceipt(any())).thenReturn(buildReceipt());

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

        verify(documentdb).setValue(cartForReceiptCaptor.capture());
        assertEquals(1, cartForReceiptCaptor.getValue().size());
        CartForReceipt captured = cartForReceiptCaptor.getValue().get(0);
        assertEquals(CartStatusType.SENT, captured.getStatus());
    }

    @Test
    void recoverInsertedReceiptMassiveSuccess() throws BizEventNotFoundException {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", CartStatusType.INSERTED.name()));

        when(cartReceiptsCosmosClientMock.getInsertedCarts(any(), eq(100)))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(generateInsertedCart()),
                                Collections.emptyMap())));

        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(generateValidBizEvent(EVENT_ID));
        when(bizEventToReceiptServiceMock.createCartReceipt(any())).thenReturn(buildReceipt());

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

        verify(documentdb).setValue(cartForReceiptCaptor.capture());
        assertEquals(1, cartForReceiptCaptor.getValue().size());
        CartForReceipt captured = cartForReceiptCaptor.getValue().get(0);
        assertEquals(CartStatusType.SENT, captured.getStatus());
    }

    @Test
    void recoverErrorIfStatusWrong() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", "a wrong statys"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

    }

    @Test
    void recoverErrorIfStatusNull() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", null));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

    }

    @Test
    void recoverFailedErrorWhenFailHandleCart() throws BizEventNotFoundException {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", CartStatusType.FAILED.name()));

        when(cartReceiptsCosmosClientMock.getFailedCarts(any(), eq(100)))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(generateCart()),
                                Collections.emptyMap())));

        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(generateValidBizEvent(EVENT_ID));
        when(bizEventToReceiptServiceMock.createCartReceipt(any())).thenAnswer(x -> {
            throw new Exception("test");
        });

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

    }

    @Test
    void recoverFailedErrorWhenFailRecover() throws BizEventNotFoundException {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", CartStatusType.FAILED.name()));

        when(cartReceiptsCosmosClientMock.getFailedCarts(any(), eq(100)))
                .thenAnswer(x -> new Exception("error"));


        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

    }

    private BizEvent generateValidBizEvent(String totalNotice){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(TOKENIZED_PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(TOKENIZED_DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice(totalNotice);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private CartForReceipt generateCart() {
        CartForReceipt cart = new CartForReceipt();
        cart.setId("1");
        cart.setStatus(CartStatusType.FAILED);
        cart.setTotalNotice(2);
        cart.setCartPaymentId(new HashSet<>(new ArrayList<>(
                List.of(new String[]{"1", "2"}))));
        return cart;
    }

    private CartForReceipt generateInsertedCart() {
        CartForReceipt cart = generateCart();
        cart.setStatus(CartStatusType.INSERTED);
        return cart;
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
                .inserted_at(0)
                .generated_at(0)
                .notified_at(0)
                .build();
    }

}