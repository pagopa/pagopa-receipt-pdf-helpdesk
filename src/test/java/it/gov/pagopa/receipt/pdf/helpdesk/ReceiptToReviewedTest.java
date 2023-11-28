package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.FeedResponse;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ReceiptToReviewedRequest;
import it.gov.pagopa.receipt.pdf.helpdesk.util.HttpResponseMessageMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptToReviewedTest {

    private static final String BIZ_EVENT_ID = "valid_biz_event_id";

    private ReceiptToReviewed function;

   @Mock
   private ReceiptCosmosClient receiptCosmosClient;

    @Captor
    private ArgumentCaptor<List<ReceiptError>> receiptErrorCaptor;

   @Test
   void requestWithValidBizEventSaveReceiptErrorInReviewed() throws ReceiptNotFoundException {
       ReceiptToReviewedRequest receiptToReviewedRequest = new ReceiptToReviewedRequest();
       receiptToReviewedRequest.setEventId(BIZ_EVENT_ID);
       HttpRequestMessage<Optional<ReceiptToReviewedRequest>> request = mock(HttpRequestMessage.class);
       when(request.getBody()).thenReturn(Optional.of(receiptToReviewedRequest));

       doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
           HttpStatus status = (HttpStatus) invocation.getArguments()[0];
           return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
       }).when(request).createResponseBuilder(any(HttpStatus.class));

       ReceiptError receiptError = ReceiptError.builder()
               .bizEventId(BIZ_EVENT_ID)
               .status(ReceiptErrorStatusType.TO_REVIEW)
               .build();
       when(receiptCosmosClient.getReceiptError(BIZ_EVENT_ID)).thenReturn(receiptError);


       function = new ReceiptToReviewed(receiptCosmosClient);

       @SuppressWarnings("unchecked")
       OutputBinding<List<ReceiptError>> documentdb = (OutputBinding<List<ReceiptError>>) spy(OutputBinding.class);

       // test execution
       AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
       assertDoesNotThrow(() -> responseMessage.set(function.run(request, documentdb)));
       assertEquals(HttpStatus.OK , responseMessage.get().getStatus());

       verify(documentdb).setValue(receiptErrorCaptor.capture());
       ReceiptError captured = receiptErrorCaptor.getValue().get(0);
       assertEquals(BIZ_EVENT_ID, captured.getBizEventId());
       assertEquals(ReceiptErrorStatusType.REVIEWED, captured.getStatus());
   }

    @Test
    void requestWithoutBizEventIdSaveMultipleReceiptErrorInReviewed() {
        ReceiptToReviewedRequest receiptToReviewedRequest = new ReceiptToReviewedRequest();
        receiptToReviewedRequest.setEventId(null);
        HttpRequestMessage<Optional<ReceiptToReviewedRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(receiptToReviewedRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));


        FeedResponse<ReceiptError> feedResponse = mock(FeedResponse.class);

        ReceiptError receiptError1 = ReceiptError.builder()
                .bizEventId("1")
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .build();
        ReceiptError receiptError2 = ReceiptError.builder()
                .bizEventId("2")
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .build();
        List<ReceiptError> listReceipt = List.of(receiptError1,receiptError2);
        when(feedResponse.getResults()).thenReturn(listReceipt);
        Iterable<FeedResponse<ReceiptError>> feedResponseIterator = List.of(feedResponse);

        when(receiptCosmosClient.getToReviewReceiptsError(any(), anyInt())).thenReturn(feedResponseIterator);

        function = new ReceiptToReviewed(receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<ReceiptError>> documentdb = (OutputBinding<List<ReceiptError>>) spy(OutputBinding.class);

        // test execution
        AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
        assertDoesNotThrow(() -> responseMessage.set(function.run(request, documentdb)));
        assertEquals(HttpStatus.OK , responseMessage.get().getStatus());

        verify(documentdb).setValue(receiptErrorCaptor.capture());

        ReceiptError captured1 = receiptErrorCaptor.getValue().get(0);
        assertEquals("1", captured1.getBizEventId());
        assertEquals(ReceiptErrorStatusType.REVIEWED, captured1.getStatus());

        ReceiptError captured2 = receiptErrorCaptor.getValue().get(1);
        assertEquals("2", captured2.getBizEventId());
        assertEquals(ReceiptErrorStatusType.REVIEWED, captured2.getStatus());
    }

    @Test
    void requestWithoutRequestBodySaveMultipleReceiptErrorInReviewed() {
        HttpRequestMessage<Optional<ReceiptToReviewedRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(null);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));


        FeedResponse<ReceiptError> feedResponse = mock(FeedResponse.class);

        ReceiptError receiptError1 = ReceiptError.builder()
                .bizEventId("1")
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .build();
        ReceiptError receiptError2 = ReceiptError.builder()
                .bizEventId("2")
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .build();
        List<ReceiptError> listReceipt = List.of(receiptError1,receiptError2);
        when(feedResponse.getResults()).thenReturn(listReceipt);
        Iterable<FeedResponse<ReceiptError>> feedResponseIterator = List.of(feedResponse);

        when(receiptCosmosClient.getToReviewReceiptsError(any(), anyInt())).thenReturn(feedResponseIterator);

        function = new ReceiptToReviewed(receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<ReceiptError>> documentdb = (OutputBinding<List<ReceiptError>>) spy(OutputBinding.class);

        // test execution
        AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
        assertDoesNotThrow(() -> responseMessage.set(function.run(request, documentdb)));
        assertEquals(HttpStatus.OK , responseMessage.get().getStatus());

        verify(documentdb).setValue(receiptErrorCaptor.capture());

        ReceiptError captured1 = receiptErrorCaptor.getValue().get(0);
        assertEquals("1", captured1.getBizEventId());
        assertEquals(ReceiptErrorStatusType.REVIEWED, captured1.getStatus());

        ReceiptError captured2 = receiptErrorCaptor.getValue().get(1);
        assertEquals("2", captured2.getBizEventId());
        assertEquals(ReceiptErrorStatusType.REVIEWED, captured2.getStatus());
    }

    @Test
    void requestWithValidBizEventIdButReceiptNotFound() throws ReceiptNotFoundException {
        ReceiptToReviewedRequest receiptToReviewedRequest = new ReceiptToReviewedRequest();
        receiptToReviewedRequest.setEventId(BIZ_EVENT_ID);
        HttpRequestMessage<Optional<ReceiptToReviewedRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(receiptToReviewedRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        when(receiptCosmosClient.getReceiptError(BIZ_EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        function = new ReceiptToReviewed(receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<ReceiptError>> documentdb = (OutputBinding<List<ReceiptError>>) spy(OutputBinding.class);

        // test execution
        AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
        assertDoesNotThrow(() -> responseMessage.set(function.run(request, documentdb)));
        assertEquals(HttpStatus.BAD_REQUEST , responseMessage.get().getStatus());

        verifyNoInteractions(documentdb);
    }

    @Test
    void requestWithoutRequestBodyDoesNotFindAnyReceiptError() {
        HttpRequestMessage<Optional<ReceiptToReviewedRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(null);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));


        FeedResponse<ReceiptError> feedResponse = mock(FeedResponse.class);

        List<ReceiptError> listReceipt = new ArrayList<>();
        when(feedResponse.getResults()).thenReturn(listReceipt);
        Iterable<FeedResponse<ReceiptError>> feedResponseIterator = List.of(feedResponse);

        when(receiptCosmosClient.getToReviewReceiptsError(any(), anyInt())).thenReturn(feedResponseIterator);

        function = new ReceiptToReviewed(receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<ReceiptError>> documentdb = (OutputBinding<List<ReceiptError>>) spy(OutputBinding.class);

        // test execution
        AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
        assertDoesNotThrow(() -> responseMessage.set(function.run(request, documentdb)));
        assertEquals(HttpStatus.OK , responseMessage.get().getStatus());

        verifyNoInteractions(documentdb);
    }
}