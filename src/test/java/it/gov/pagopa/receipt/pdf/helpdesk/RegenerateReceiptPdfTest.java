package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.CosmosItemResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.helpdesk.model.request.RegenerateReceiptRequest;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.helpdesk.util.HttpResponseMessageMock;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.GenerateReceiptUtils;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.ObjectMapperUtils;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegenerateReceiptPdfTest {

    private static final BizEvent bizEvent;

    static {
        try {
            bizEvent = ObjectMapperUtils.mapString("{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":{\"transaction\":{\"origin\":\"IO\",\"psp\":{\"businessName\":\"Nexi\"}},\"wallet\":{\"info\":{\"brand\":\"MASTER\"}}},\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}",BizEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static final long ORIGINAL_GENERATED_AT = 0L;

    private BizEventToReceiptService bizEventToReceiptService;

    private GenerateReceiptPdfService generateReceiptPdfServiceMock;
    private ReceiptCosmosClient receiptCosmosClientMock;
    private BizEventCosmosClient bizEventCosmosClient;
    private ExecutionContext executionContextMock;
    private RegenerateReceiptPdf sut;
    @Spy
    private OutputBinding<Receipt> documentdb;

    @BeforeEach
    void setUp() {
        generateReceiptPdfServiceMock = mock(GenerateReceiptPdfService.class);
        bizEventToReceiptService = mock(BizEventToReceiptService.class);
        receiptCosmosClientMock = mock(ReceiptCosmosClient.class);
        bizEventCosmosClient = mock(BizEventCosmosClient.class);
        executionContextMock = mock(ExecutionContext.class);

        sut = spy(new RegenerateReceiptPdf(
                bizEventCosmosClient, receiptCosmosClientMock, generateReceiptPdfServiceMock, bizEventToReceiptService));
    }

    @Test
    @SneakyThrows
    void regeneratePDFSuccess() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.mapString("{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":{\"transaction\":{\"origin\":\"IO\",\"psp\":{\"businessName\":\"Nexi\"}},\"wallet\":{\"info\":{\"brand\":\"MASTER\"}}},\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}",BizEvent.class);
    	
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        receipt.setMdAttachPayer(null);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        
        CosmosItemResponse<Object> mockDeleteResponse = mock(CosmosItemResponse.class);
        doReturn(204).when(mockDeleteResponse).getStatusCode();
        doReturn(mockDeleteResponse).when(receiptCosmosClientMock).deleteReceipt(any(Receipt.class));

        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(200,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
    }
    
    @Test
    @SneakyThrows
    void regeneratePDFSameDebtorAndPayerSuccess() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.map(bizEvent, new BizEvent());
    	
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        receipt.getEventData().setDebtorFiscalCode("same cf debtor and payer");
        receipt.getEventData().setPayerFiscalCode("same cf debtor and payer");
        receipt.getMdAttach().setUrl(null);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        
        CosmosItemResponse<Object> mockDeleteResponse = mock(CosmosItemResponse.class);
        doReturn(204).when(mockDeleteResponse).getStatusCode();
        doReturn(mockDeleteResponse).when(receiptCosmosClientMock).deleteReceipt(any(Receipt.class));
        
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());

        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));
        

        // test execution
        assertEquals(200,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
    }
    
    @Test
    @SneakyThrows
    void regeneratePDFIOException() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.map(bizEvent, new BizEvent());
    	
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        
        CosmosItemResponse<Object> mockDeleteResponse = mock(CosmosItemResponse.class);
        doReturn(204).when(mockDeleteResponse).getStatusCode();
        doReturn(mockDeleteResponse).when(receiptCosmosClientMock).deleteReceipt(any(Receipt.class));
        
        MockedStatic<GenerateReceiptUtils> mockedStaticGenerateReceiptUtils = mockStatic(GenerateReceiptUtils.class);
        when(GenerateReceiptUtils.createWorkingDirectory()).thenThrow(IOException.class);
        when(GenerateReceiptUtils.getReceipt(any(), any(), any(), any())).thenReturn(receipt);
       
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());
        
        mockedStaticGenerateReceiptUtils.close();
    }

    @Test
    @SneakyThrows
    void regeneratePDFSuccessRegenPayer() {
        int numRetry = 0;
        
        BizEvent localBizEvent = ObjectMapperUtils.mapString("{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":{\"transaction\":{\"origin\":\"IO\",\"psp\":{\"businessName\":\"Nexi\"}},\"wallet\":{\"info\":{\"brand\":\"MASTER\"}}},\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}",BizEvent.class);
        
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        receipt.getEventData().setPayerFiscalCode(null);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        
        CosmosItemResponse<Object> mockDeleteResponse = mock(CosmosItemResponse.class);
        doReturn(204).when(mockDeleteResponse).getStatusCode();
        doReturn(mockDeleteResponse).when(receiptCosmosClientMock).deleteReceipt(any(Receipt.class));

        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        
        when(request.getQueryParameters()).thenReturn(Map.of("isCart", "false"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() ->
        sut.run(request, "1", documentdb, executionContextMock));
        
        System.out.println("Response code: " + response.getStatusCode());
        System.out.println("Response body: " + response.getBody());
        
        assertEquals(HttpStatus.SC_OK, response.getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        verify(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
    }

    @Test
    @SneakyThrows
    void regeneratePDFInsuccessMissingRequestData() {

        RegenerateReceiptRequest regenerateReceiptRequest = new RegenerateReceiptRequest();

        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_BAD_REQUEST,assertDoesNotThrow(() -> sut.run(request, null, documentdb, executionContextMock)).getStatusCode());

        verifyNoInteractions(receiptCosmosClientMock);
        verifyNoInteractions(generateReceiptPdfServiceMock);
    }

    @Test
    @SneakyThrows
    void regeneratePDFErrorMissingEventData() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.mapString("{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":{\"transaction\":{\"origin\":\"IO\",\"psp\":{\"businessName\":\"Nexi\"}},\"wallet\":{\"info\":{\"brand\":\"MASTER\"}}},\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}",BizEvent.class);
    	
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        receipt.setEventData(null);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        CosmosItemResponse<Object> mockDeleteResponse = mock(CosmosItemResponse.class);
        doReturn(204).when(mockDeleteResponse).getStatusCode();
        doReturn(mockDeleteResponse).when(receiptCosmosClientMock).deleteReceipt(any(Receipt.class));

        RegenerateReceiptRequest regenerateReceiptRequest = new RegenerateReceiptRequest();
        regenerateReceiptRequest.setEventId("1");

        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
    }
    
    @Test
    @SneakyThrows
    void regeneratePDFErrorMissingBizEvent() {

        when(bizEventCosmosClient.getBizEventDocument(anyString())).thenThrow(BizEventNotFoundException.class);
        
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));
        
        // test execution
        assertEquals(HttpStatus.SC_BAD_REQUEST,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());
    }
    
    @Test
    @SneakyThrows
    void regeneratePDFReceiptNotFoundSuccess() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.map(bizEvent, new BizEvent());
    	
    	int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        when(receiptCosmosClientMock.getReceiptDocument(anyString())).thenThrow(new ReceiptNotFoundException("KO")).thenReturn(receipt);
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        
        Receipt createdReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        MockedStatic<BizEventToReceiptUtils> mockedStaticBizEventToReceiptUtils = mockStatic(BizEventToReceiptUtils.class);
        when(BizEventToReceiptUtils.createReceipt(any(), any(), any())).thenReturn(createdReceipt);
        when(BizEventToReceiptUtils.getTotalNotice(any(), any(), any())).thenReturn(1);
        when(BizEventToReceiptUtils.isReceiptStatusValid(any())).thenReturn(true);
        
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_OK,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        
        mockedStaticBizEventToReceiptUtils.close();

    }
    
    @Test
    @SneakyThrows
    void regeneratePDFReceiptFileGenerationError() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.mapString("{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":{\"transaction\":{\"origin\":\"IO\",\"psp\":{\"businessName\":\"Nexi\"}},\"wallet\":{\"info\":{\"brand\":\"MASTER\"}}},\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}",BizEvent.class);
    	
    	int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry); 
        receipt.setMdAttachPayer(null);
        receipt.setMdAttach(null);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        when(receiptCosmosClientMock.getReceiptDocument(anyString())).thenReturn(receipt);
        doReturn(PdfGeneration.builder().debtorMetadata(PdfMetadata.builder().errorMessage("error pdf").statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).build()).build())
        .when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(false).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        
        CosmosItemResponse<Object> mockDeleteResponse = mock(CosmosItemResponse.class);
        doReturn(204).when(mockDeleteResponse).getStatusCode();
        doReturn(mockDeleteResponse).when(receiptCosmosClientMock).deleteReceipt(any(Receipt.class));
        
        
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
    }
    
    @Test
    @SneakyThrows
    void regeneratePDFCartReceiptNotFoundException() { 
    	BizEvent bizEventCopy = ObjectMapperUtils.map(bizEvent, new BizEvent());
        bizEventCopy.getPaymentInfo().setTotalNotice("2");
        List<BizEvent> bizEventList = Arrays.asList(bizEventCopy);
        
        doReturn(bizEventList).when(bizEventToReceiptService).getCartBizEvents(anyString());
        when(receiptCosmosClientMock.getReceiptDocument(anyString())).thenThrow(new ReceiptNotFoundException("KO"));

        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);
        when(request.getQueryParameters()).thenReturn(Collections.singletonMap("isCart", "true"));
        
        Receipt createdReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.INSERTED, 0);
        MockedStatic<BizEventToReceiptUtils> mockedStaticBizEventToReceiptUtils = mockStatic(BizEventToReceiptUtils.class);
        when(BizEventToReceiptUtils.createReceipt(any(), any(), any())).thenReturn(createdReceipt);
        when(BizEventToReceiptUtils.getTotalNotice(any(), any(), any())).thenReturn(2);
        when(BizEventToReceiptUtils.isReceiptStatusValid(any())).thenReturn(true);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());
        verify(receiptCosmosClientMock).getReceiptDocument(anyString());        
        
        mockedStaticBizEventToReceiptUtils.close();
    }
    
    @Test
    @SneakyThrows
    void regeneratePDFReceiptNotFoundAndWithoutAttachmentSuccess() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.map(bizEvent, new BizEvent());
    	
    	int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        receipt.setMdAttach(null);
        receipt.setMdAttachPayer(null);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        when(receiptCosmosClientMock.getReceiptDocument(anyString())).thenThrow(new ReceiptNotFoundException("KO")).thenReturn(receipt);
        doReturn(new PdfGeneration()).when(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
        doReturn(true).when(generateReceiptPdfServiceMock).verifyAndUpdateReceipt(any(), any());
        
        Receipt createdReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        MockedStatic<BizEventToReceiptUtils> mockedStaticBizEventToReceiptUtils = mockStatic(BizEventToReceiptUtils.class);
        when(BizEventToReceiptUtils.createReceipt(any(), any(), any())).thenReturn(createdReceipt);
        when(BizEventToReceiptUtils.getTotalNotice(any(), any(), any())).thenReturn(1);
        when(BizEventToReceiptUtils.isReceiptStatusValid(any())).thenReturn(true);
        
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_OK,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        
        mockedStaticBizEventToReceiptUtils.close();

    }

    @Test
    @SneakyThrows
    void regeneratePDFReceiptNotFoundError() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.map(bizEvent, new BizEvent());
    	
    	int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        when(receiptCosmosClientMock.getReceiptDocument(anyString())).thenThrow(new ReceiptNotFoundException("KO")).thenReturn(receipt);
        
        Receipt createdReceipt = buildNewCreatedReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);
        MockedStatic<BizEventToReceiptUtils> mockedStaticBizEventToReceiptUtils = mockStatic(BizEventToReceiptUtils.class);
        when(BizEventToReceiptUtils.createReceipt(any(), any(), any())).thenReturn(createdReceipt);
        when(BizEventToReceiptUtils.getTotalNotice(any(), any(), any())).thenReturn(1);
        // not valid receipt
        when(BizEventToReceiptUtils.isReceiptStatusValid(any())).thenReturn(false);
        
        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        
        mockedStaticBizEventToReceiptUtils.close();

    }    
    
    @Test
    @SneakyThrows
    void regeneratePDFErrorReceiptGenerationException() {
    	
    	BizEvent localBizEvent = ObjectMapperUtils.mapString("{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":{\"transaction\":{\"origin\":\"IO\",\"psp\":{\"businessName\":\"Nexi\"}},\"wallet\":{\"info\":{\"brand\":\"MASTER\"}}},\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}",BizEvent.class);
    	
        int numRetry = 0;
        Receipt receipt = buildReceiptWithStatus(ReceiptStatusType.INSERTED, numRetry);

        doReturn(localBizEvent).when(bizEventCosmosClient).getBizEventDocument(anyString());
        doReturn(receipt).when(receiptCosmosClientMock).getReceiptDocument(anyString());
        when(generateReceiptPdfServiceMock.generateReceipts(any(), any(), any())).thenAnswer(invocationOnMock -> {
            throw new Exception();
        });
        
        CosmosItemResponse<Object> mockDeleteResponse = mock(CosmosItemResponse.class);
        doReturn(204).when(mockDeleteResponse).getStatusCode();
        doReturn(mockDeleteResponse).when(receiptCosmosClientMock).deleteReceipt(any(Receipt.class));


        HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            com.microsoft.azure.functions.HttpStatus status = (com.microsoft.azure.functions.HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(com.microsoft.azure.functions.HttpStatus.class));

        // test execution
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,assertDoesNotThrow(() -> sut.run(request, "1", documentdb, executionContextMock)).getStatusCode());

        verify(receiptCosmosClientMock).getReceiptDocument(anyString());
        verify(generateReceiptPdfServiceMock).generateReceipts(any(), any(), any());
    }



    private Receipt buildReceiptWithStatus(ReceiptStatusType receiptStatusType, int numRetry) {
        return Receipt.builder()
        		.id("id")
                .eventData(EventData.builder()
                        .debtorFiscalCode("cd debtor")
                        .payerFiscalCode("cf payer")
                        .build())
                .mdAttach(ReceiptMetadata.builder().name("DEBTOR_NAME").url("DEBTOR_URL").build())
                .mdAttachPayer(ReceiptMetadata.builder().name("PAYER_NAME").url("PAYER_URL").build())
                .eventId("biz-event-id")
                .status(receiptStatusType)
                .numRetry(numRetry)
                .generated_at(ORIGINAL_GENERATED_AT)
                .inserted_at(0L)
                .notified_at(0L)
                .build();
    }
    
    private Receipt buildNewCreatedReceiptWithStatus(ReceiptStatusType receiptStatusType, int numRetry) {
    	CartItem ci = CartItem.builder().payeeName("payee").subject("TARI").build();
        return Receipt.builder()
        		.id("new_created_receipt_id")
                .eventData(EventData.builder()
                        .debtorFiscalCode("tokenizedDebtorFiscalCode")
                        .payerFiscalCode("tokenizedPayerFiscalCode")
                        .cart(List.of(ci))
                        .transactionCreationDate(new Date().toString())
                        .build())
                .mdAttach(ReceiptMetadata.builder().name("DEBTOR_NAME").url("DEBTOR_URL").build())
                .mdAttachPayer(ReceiptMetadata.builder().name("PAYER_NAME").url("PAYER_URL").build())
                .eventId("biz-event-id")
                .status(receiptStatusType)
                .numRetry(numRetry)
                .generated_at(ORIGINAL_GENERATED_AT)
                .inserted_at(0L)
                .notified_at(0L)
                .build();
    }
}
