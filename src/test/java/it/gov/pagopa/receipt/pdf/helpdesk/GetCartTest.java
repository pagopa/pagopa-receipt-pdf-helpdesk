package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
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

class GetCartTest {

    private static final String CART_ID = "cartId";

    @Mock
    private ExecutionContext executionContextMock;

    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    private GetCart sut;

    private AutoCloseable closeable;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new GetCart(receiptCosmosServiceMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }


    @Test
    void getCartReceiptSuccess() throws CartNotFoundException {
        CartForReceipt cart = new CartForReceipt();
        when(receiptCosmosServiceMock.getCart(CART_ID)).thenReturn(cart);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, CART_ID, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());
        assertEquals(cart, response.getBody());
    }

    @Test
    void getCartForMissingEventId() {
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
    void getCartReceiptNotFound() throws CartNotFoundException {
        when(receiptCosmosServiceMock.getCart(CART_ID)).thenThrow(CartNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, CART_ID, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());
    }
}