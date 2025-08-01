package it.gov.pagopa.receipt.pdf.helpdesk.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;

import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.*;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.UserType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class BizEventToReceiptUtilsTest {
    public static final String VALID_IO_CHANNEL = "IO";
    public static final String INVALID_REMITTANCE_INFORMATION = "pagamento multibeneficiario";
    private final String EVENT_ID = "a valid id";
    private final String PAYER_FISCAL_CODE = "AAAAAA00A00A000D";
    private final String DEBTOR_FISCAL_CODE = "AAAAAA00A00A000P";
    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    public static final String REMITTANCE_INFORMATION_PAYMENT_INFO = "TARI 2021";
    public static final String REMITTANCE_INFORMATION_TRANSFER_LIST = "EXAMPLE/TXT/TARI 2021/EXAMPLE";
    public static final String REMITTANCE_INFORMATION_TRANSFER_LIST_FORMATTED = "TARI 2021/EXAMPLE";
    public static final String TRANSFER_AMOUNT_HIGHEST = "10000.00";
    public static final String TRANSFER_AMOUNT_MEDIUM = "20.00";
    public static final String TRANSFER_AMOUNT_LOWEST = "10.00";
    public static final String AUTHENTICATED_CHANNELS = "IO,OTHER VALID ORIGIN,ANOTHER VALID,CHECKOUT";
    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;
    @Mock
    private ReceiptCosmosClientImpl receiptCosmosClient;
    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;
    @Mock
    private ReceiptQueueClientImpl queueClient;
    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables(
            "AUTHENTICATED_CHANNELS", AUTHENTICATED_CHANNELS, "ECOMMERCE_FILTER_ENABLED", "true");

    private final Logger logger = LoggerFactory.getLogger(BizEventToReceiptUtilsTest.class);

    @Test
    void createReceiptSuccessWithPaymentInfo() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(false, false), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, receipt.getEventData().getPayerFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_PAYMENT_INFO, receipt.getEventData().getCart().get(0).getSubject());
    }

    @Test
    void createReceiptSuccessWithoutPaymentInfoButWithTransferList() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(false, true), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, receipt.getEventData().getPayerFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_TRANSFER_LIST_FORMATTED, receipt.getEventData().getCart().get(0).getSubject());
    }

    @Test
    void createReceiptSuccessWithPaymentInfoRemittanceInvalidButWithTransferList() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);
        BizEvent bizEvent = generateValidBizEvent(false, true);
        bizEvent.getPaymentInfo().setRemittanceInformation(INVALID_REMITTANCE_INFORMATION);
        Receipt receipt = BizEventToReceiptUtils.createReceipt(bizEvent, receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, receipt.getEventData().getPayerFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_TRANSFER_LIST_FORMATTED, receipt.getEventData().getCart().get(0).getSubject());
    }

    @Test
    void createReceiptSuccessWithoutRemittanceInformation() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(true, false), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, receipt.getEventData().getPayerFiscalCode());
        assertNull(receipt.getEventData().getCart().get(0).getSubject());
    }

    @Test
    void createReceiptSuccessWithTokenizerFailed() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenThrow(new PDVTokenizerException("exception", HttpStatus.I_AM_A_TEAPOT.value()));

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(false, false), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertNull(receipt.getEventData());
        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
    }

    @Test
    void createReceiptSuccessWithChannelOriginInTransactionInfo() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEventWithChannelOriginInTransactionInfo(null, null), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, receipt.getEventData().getPayerFiscalCode());
    }
    
    @Test
    void createReceiptSuccessWithCheckoutChannelOriginInTransactionInfo() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEventWithChannelOriginInTransactionInfo("CHECKOUT", UserType.REGISTERED), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, receipt.getEventData().getPayerFiscalCode());
    }

    @Test
    void payerNotGeneratedWithoutChannelOrigin() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEventWithoutChannelOrigin(), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertNull(receipt.getEventData().getPayerFiscalCode());
    }
    
    @Test
    void payerReceiptNotGeneratedWithUserNotRegistered() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEventWithChannelOriginInTransactionInfo("CHECKOUT", UserType.GUEST), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertNull(receipt.getEventData().getPayerFiscalCode());
    }

    @Test
    void payerNotGeneratedWithInvalidChannelOrigin() throws PDVTokenizerException, JsonProcessingException {
        environmentVariables.set("AUTHENTICATED_CHANNELS", "DIFFERENT ORIGIN");
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, queueClient, bizEventCosmosClientMock, receiptCosmosClient);

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(false, false), receiptService, null, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertNull(receipt.getEventData().getPayerFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_PAYMENT_INFO, receipt.getEventData().getCart().get(0).getSubject());
    }
    
    @Test
    void eCommerceAndTotalNoticeTest() throws PDVTokenizerException, JsonProcessingException {
    	BizEvent bizEvent = BizEvent.builder()
    			.eventStatus(BizEventStatusType.DONE)
    			.transactionDetails(TransactionDetails.builder().info(InfoTransaction.builder().clientId("CHECKOUT").build()).user(User.builder().fiscalCode(DEBTOR_FISCAL_CODE).build()).build())
    			.debtor(Debtor.builder().entityUniqueIdentifierValue(DEBTOR_FISCAL_CODE).build())
    			.paymentInfo(PaymentInfo.builder().totalNotice("1").build())
    			.build();
    	boolean result = BizEventToReceiptUtils.isBizEventInvalid(bizEvent, mock(ExecutionContext.class), logger);
    	Integer totalNotice = BizEventToReceiptUtils.getTotalNotice(bizEvent, mock(ExecutionContext.class), logger);
    	assertEquals(true, result);
        assertEquals(1, totalNotice);
    }
    
    @Test
    void invalidBizEventPartOfPaymentCartTest() throws IOException {
    	
    	BizEvent bizEvent = ObjectMapperUtils.readModelFromFile("biz-events/bizEvent.json", BizEvent.class);
    	
    	boolean result = BizEventToReceiptUtils.isBizEventInvalid(bizEvent, mock(ExecutionContext.class), logger);
    	Integer totalNotice = BizEventToReceiptUtils.getTotalNotice(bizEvent, mock(ExecutionContext.class), logger);
    	assertEquals(true, result);
        assertEquals(2, totalNotice);
    }

    private BizEvent generateValidBizEvent(boolean withoutRemittanceInformation, boolean withTransferList) {
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transaction.setOrigin(VALID_IO_CHANNEL);
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("1");
        if (!withoutRemittanceInformation) {
            if (withTransferList) {
                List<Transfer> transferList = List.of(
                        Transfer.builder()
                                .amount(TRANSFER_AMOUNT_LOWEST)
                                .remittanceInformation("not to show")
                                .build(),
                        Transfer.builder()
                                .amount(TRANSFER_AMOUNT_MEDIUM)
                                .remittanceInformation("not to show")
                                .build(),
                        Transfer.builder()
                                .amount(TRANSFER_AMOUNT_HIGHEST)
                                .remittanceInformation(REMITTANCE_INFORMATION_TRANSFER_LIST)
                                .build()
                );
                item.setTransferList(transferList);
            } else {
                paymentInfo.setRemittanceInformation(REMITTANCE_INFORMATION_PAYMENT_INFO);
            }
        }
        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private BizEvent generateValidBizEventWithChannelOriginInTransactionInfo(String validChannel, UserType userType) {
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        if (userType != null) {
			User user = new User();
			user.setFiscalCode(PAYER_FISCAL_CODE);
			user.setType(userType);
			transactionDetails.setUser(user);
		}
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);
        InfoTransaction infoTransaction = new InfoTransaction();
        infoTransaction.setClientId(validChannel == null ? VALID_IO_CHANNEL : validChannel);
        transactionDetails.setInfo(infoTransaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("1");

        List<Transfer> transferList = List.of(
                Transfer.builder()
                        .amount(TRANSFER_AMOUNT_LOWEST)
                        .remittanceInformation("not to show")
                        .build(),
                Transfer.builder()
                        .amount(TRANSFER_AMOUNT_MEDIUM)
                        .remittanceInformation("not to show")
                        .build(),
                Transfer.builder()
                        .amount(TRANSFER_AMOUNT_HIGHEST)
                        .remittanceInformation(REMITTANCE_INFORMATION_TRANSFER_LIST)
                        .build()
        );
        item.setTransferList(transferList);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private BizEvent generateValidBizEventWithoutChannelOrigin() {
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("1");

        List<Transfer> transferList = List.of(
                Transfer.builder()
                        .amount(TRANSFER_AMOUNT_LOWEST)
                        .remittanceInformation("not to show")
                        .build(),
                Transfer.builder()
                        .amount(TRANSFER_AMOUNT_MEDIUM)
                        .remittanceInformation("not to show")
                        .build(),
                Transfer.builder()
                        .amount(TRANSFER_AMOUNT_HIGHEST)
                        .remittanceInformation(REMITTANCE_INFORMATION_TRANSFER_LIST)
                        .build()
        );
        item.setTransferList(transferList);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

}