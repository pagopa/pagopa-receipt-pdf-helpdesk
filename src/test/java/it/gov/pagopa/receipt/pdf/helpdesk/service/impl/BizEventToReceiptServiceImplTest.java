package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import com.microsoft.azure.functions.HttpStatus;

import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.service.PDVTokenizerServiceRetryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class BizEventToReceiptServiceImplTest {

    private BizEventToReceiptServiceImpl bizEventToReceiptService;
    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;
    @Mock
    private ReceiptCosmosClientImpl receiptCosmosClient;
    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;
    @Mock
    private ReceiptQueueClientImpl queueClient;


    @BeforeEach
    public void init() {
        bizEventToReceiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock,
                queueClient,
                bizEventCosmosClientMock,
                receiptCosmosClient
        );
    }

    @Test
    void run_OK_getCartBizEvents() {
        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(feedResponseMock.getResults()).thenReturn(Collections.singletonList(new BizEvent()));
        doReturn(Collections.singletonList(feedResponseMock)).when(bizEventCosmosClientMock)
                .getAllBizEventDocument(Mockito.eq("1"), any(), any());
        assertDoesNotThrow(() -> bizEventToReceiptService.getCartBizEvents("1"));
    }
    
    @SuppressWarnings("unchecked")
	@Test
    void run_OK_handleSaveRecipt() {
    	Receipt receipt = mock(Receipt.class);
    	CosmosItemResponse<Receipt> response = mock(CosmosItemResponse.class);
        when(receiptCosmosClient.saveReceipts(any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        
        try {
        	bizEventToReceiptService.handleSaveReceipt(receipt,  ReceiptStatusType.INSERTED);
        	verify(receiptCosmosClient, times(1)).saveReceipts(receipt);
        	bizEventToReceiptService.handleSaveReceipt(receipt,  ReceiptStatusType.GENERATED);
        	verify(receiptCosmosClient, times(2)).saveReceipts(receipt);
        	bizEventToReceiptService.handleSaveReceipt(receipt,  ReceiptStatusType.IO_NOTIFIED);
        	verify(receiptCosmosClient, times(3)).saveReceipts(receipt);
        	// default behavior
        	bizEventToReceiptService.handleSaveReceipt(receipt,  ReceiptStatusType.UNABLE_TO_SEND);
        	verify(receiptCosmosClient, times(4)).saveReceipts(receipt);
        } catch (Exception e) {
			fail();
		}
    }

}
