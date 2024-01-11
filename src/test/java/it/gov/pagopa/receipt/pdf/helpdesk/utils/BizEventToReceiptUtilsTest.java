package it.gov.pagopa.receipt.pdf.helpdesk.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.*;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.BizEventStatusType;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizEventToReceiptUtilsTest {
    private final String EVENT_ID = "a valid id";
    private final String PAYER_FISCAL_CODE = "a valid payer CF";
    private final String DEBTOR_FISCAL_CODE = "a valid debtor CF";
    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    public static final String REMITTANCE_INFORMATION_PAYMENT_INFO = "TARI 2021";
    public static final String REMITTANCE_INFORMATION_TRANSFER_LIST = "EXAMPLE/TXT/TARI 2021/EXAMPLE";
    public static final String REMITTANCE_INFORMATION_TRANSFER_LIST_FORMATTED = "TARI 2021/EXAMPLE";
    public static final String TRANSFER_AMOUNT_HIGHEST = "10000.00";
    public static final String TRANSFER_AMOUNT_MEDIUM = "20.00";
    public static final String TRANSFER_AMOUNT_LOWEST = "10.00";

    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;
    private final Logger logger = LoggerFactory.getLogger(BizEventToReceiptUtilsTest.class);

    @Test
    void createReceiptSuccessWithPaymentInfo() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, mock(ReceiptQueueClientImpl.class), mock(BizEventCosmosClientImpl.class), mock(ReceiptCosmosClientImpl.class));

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(false,false), receiptService, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_PAYMENT_INFO, receipt.getEventData().getCart().get(0).getSubject());
    }

    @Test
    void createReceiptSuccessWithoutPaymentInfoButWithTransferList() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, mock(ReceiptQueueClientImpl.class), mock(BizEventCosmosClientImpl.class), mock(ReceiptCosmosClientImpl.class));

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(false,true), receiptService, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_TRANSFER_LIST_FORMATTED, receipt.getEventData().getCart().get(0).getSubject());
    }

    @Test
    void createReceiptSuccessWithoutRemittanceInformation() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, mock(ReceiptQueueClientImpl.class), mock(BizEventCosmosClientImpl.class), mock(ReceiptCosmosClientImpl.class));

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(true,false), receiptService, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertNull(receipt.getEventData().getCart().get(0).getSubject());
    }

    @Test
    void createReceiptSuccessWithTokenizerFailed() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenThrow(new PDVTokenizerException("exception", HttpStatus.I_AM_A_TEAPOT.value()));

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, mock(ReceiptQueueClientImpl.class), mock(BizEventCosmosClientImpl.class), mock(ReceiptCosmosClientImpl.class));

        Receipt receipt = BizEventToReceiptUtils.createReceipt(generateValidBizEvent(false,false), receiptService, logger);

        assertEquals(EVENT_ID, receipt.getEventId());
        assertNotNull(receipt.getId());
        assertNull(receipt.getEventData());
        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
    }

    private BizEvent generateValidBizEvent( boolean withoutRemittanceInformation, boolean withTransferList){
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
        if(!withoutRemittanceInformation){
            if(withTransferList){
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
}