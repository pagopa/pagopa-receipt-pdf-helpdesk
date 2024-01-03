package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class BizEventToReceiptServiceImplTest {

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
    public void run_OK_getCartBizEvents() {
        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(feedResponseMock.getResults()).thenReturn(Collections.singletonList(new BizEvent()));
        doReturn(Collections.singletonList(feedResponseMock)).when(bizEventCosmosClientMock)
                .getAllBizEventDocument(Mockito.eq(1), any(), any());
        assertDoesNotThrow(() -> bizEventToReceiptService.getCartBizEvents(1));
    }

}
