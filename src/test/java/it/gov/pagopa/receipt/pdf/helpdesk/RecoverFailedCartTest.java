package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.*;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
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
class RecoverFailedCartTest {

    private final String PAYER_FISCAL_CODE = "a valid payer CF";
    private final String DEBTOR_FISCAL_CODE = "a valid debtor CF";
    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private final String EVENT_ID = "a valid id";

    public static final String HTTP_MESSAGE_ERROR = "an error occured";

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private ReceiptQueueClient queueClientMock;
    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;

    @Mock
    private ReceiptCosmosClient receiptCosmosClient;

    @Mock
    private CartReceiptsCosmosClientImpl cartReceiptsCosmosClient;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Captor
    private ArgumentCaptor<CartForReceipt> cartForReceiptArgumentCaptor;

    @Spy
    private OutputBinding<CartForReceipt> documentdb;

    private AutoCloseable closeable;

    private RecoverFailedCart sut;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClientMock, bizEventCosmosClientMock, receiptCosmosClient);
        sut = spy(new RecoverFailedCart(receiptService, cartReceiptsCosmosClient));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }


    @Test
    void requestOnValidCartShouldResend() throws CartNotFoundException {

        Response<SendMessageResult> queueResponse = mock(Response.class);
        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClientMock.sendMessageToQueue(anyString())).thenReturn(queueResponse);

        when(cartReceiptsCosmosClient.getCartItem(EVENT_ID)).thenReturn(generateCart());

        CosmosItemResponse<Receipt> responseCosmos = Mockito.mock(CosmosItemResponse.class);
        when(responseCosmos.getStatusCode()).thenReturn(201);
        when(receiptCosmosClient.saveReceipts(any())).thenReturn(responseCosmos);

        FeedResponse feedResponseMock = mock(FeedResponse.class);
        List<BizEvent> receiptList = Collections.singletonList(generateValidBizEvent("1"));
        when(feedResponseMock.getResults()).thenReturn(receiptList);
        doReturn(Collections.singletonList(feedResponseMock)).when(bizEventCosmosClientMock)
                .getAllBizEventDocument(Mockito.eq("1"), any(), any());

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(cartForReceiptArgumentCaptor.capture());
        CartForReceipt captured = cartForReceiptArgumentCaptor.getValue();
        assertEquals(CartStatusType.SENT, captured.getStatus());
    }

    @Test
    void requestOnUncompleteCartShouldntResend() throws CartNotFoundException {

        Response<SendMessageResult> queueResponse = mock(Response.class);
        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClientMock.sendMessageToQueue(anyString())).thenReturn(queueResponse);

        CartForReceipt cart = generateCart();
        cart.setCartPaymentId(Collections.emptySet());
        when(cartReceiptsCosmosClient.getCartItem(EVENT_ID)).thenReturn(cart);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

    }

    @Test
    void requestOnAlreadySentCartShouldntResend() throws CartNotFoundException {

        Response<SendMessageResult> queueResponse = mock(Response.class);
        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClientMock.sendMessageToQueue(anyString())).thenReturn(queueResponse);

        CartForReceipt cart = generateCart();
        cart.setStatus(CartStatusType.SENT);
        when(cartReceiptsCosmosClient.getCartItem(EVENT_ID)).thenReturn(cart);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

    }

    @Test
    void requestWithMissingCartOnRequestIdShouldReturnNotFound() throws CartNotFoundException {
        when(cartReceiptsCosmosClient.getCartItem(EVENT_ID)).thenThrow(CartNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());
    }

    @Test
    void requestWithMissingCartIdShouldReturnError() throws CartNotFoundException {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, null, documentdb, contextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());
    }

    @Test
    @SneakyThrows
    void errorTokenizingFiscalCodes() {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID)).thenReturn(generateValidBizEvent("1"));
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenThrow(ReceiptNotFoundException.class);
        lenient().when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenThrow(new PDVTokenizerException(HTTP_MESSAGE_ERROR, org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR));


        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        when(cartReceiptsCosmosClient.getCartItem(EVENT_ID)).thenReturn(generateCart());

        FeedResponse feedResponseMock = mock(FeedResponse.class);
        List<BizEvent> receiptList = Collections.singletonList(generateValidBizEvent("1"));
        when(feedResponseMock.getResults()).thenReturn(receiptList);
        doReturn(Collections.singletonList(feedResponseMock)).when(bizEventCosmosClientMock)
                .getAllBizEventDocument(Mockito.eq("1"), any(), any());
        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemJson.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verifyNoInteractions(queueClientMock);
    }

    @Test
    @SneakyThrows
    void errorAddingMessageToQueue() {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE))
                .thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        Response<SendMessageResult> queueResponse = mock(Response.class);
        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.FORBIDDEN.value());
        when(queueClientMock.sendMessageToQueue(anyString())).thenReturn(queueResponse);

        when(cartReceiptsCosmosClient.getCartItem(EVENT_ID)).thenReturn(generateCart());

        FeedResponse feedResponseMock = mock(FeedResponse.class);
        List<BizEvent> receiptList = Collections.singletonList(generateValidBizEvent("1"));
        when(feedResponseMock.getResults()).thenReturn(receiptList);
        doReturn(Collections.singletonList(feedResponseMock)).when(bizEventCosmosClientMock)
                .getAllBizEventDocument(Mockito.eq("1"), any(), any());
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));
    }



    private BizEvent generateValidBizEvent(String totalNotice){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);
        transaction.setOrigin("IO");

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice(totalNotice);
        paymentInfo.setAmount("102.30");

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

}