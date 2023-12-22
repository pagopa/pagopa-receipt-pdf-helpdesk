package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.*;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.TemplateDataField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

class BuildTemplateServiceImplTest {
    public static final String COMPANY_NAME = "PA paolo";
    public static final String ID_PSP = "ID_PSP";
    public static final String DEBTOR_FULL_NAME = "John Doe";
    public static final String DEBTOR_FULL_NAME_INVALID = "-- --";
    public static final String DEBTOR_FULL_NAME_SPECIAL_CHAR = "John,Doe:Megacorp;SRL::Avenue;Street/";
    public static final String DEBTOR_FULL_NAME_SPECIAL_CHAR_FORMATTED = "John Doe Megacorp SRL Avenue Street ";
    public static final String DEBTOR_VALID_CF = "CF_DEBTOR";
    public static final String PAYER_FULL_NAME = "John Doe PAYER";
    public static final String PAYER_VALID_CF = "CF_PAYER";
    public static final String HOLDER_FULL_NAME = "John Doe HOLDER";
    public static final String PAYMENT_TOKEN = "9a9bad2caf604b86a339476373c659b0";
    public static final String AMOUNT_WITHOUT_CENTS = "7000";
    public static final String FEE_WITH_SINGLE_DIGIT_CENTS = "77.7";
    public static final long AMOUNT_LONG = 700000L;
    public static final long FEE_LONG = 7770L;
    public static final long GRAND_TOTAL_LONG = 707770L;
    public static final String FORMATTED_AMOUNT = "7.000,00";
    public static final String FORMATTED_FEE = "77,70";
    public static final String FORMATTED_GRAND_TOTAL = "7.077,70";
    public static final String REMITTANCE_INFORMATION = "TARI 2021";
    public static final String IUR = "IUR";
    public static final String BRAND = "MASTER";
    public static final long ID_TRANSACTION = 1L;
    public static final String RRN = "rrn";
    public static final String AUTH_CODE = "authCode";
    public static final String DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER = "2023-11-14T19:31:55.484065";
    public static final String DATE_TIME_TIMESTAMP_MILLISECONDS_DST_SUMMER = "2023-08-05T11:11:54.484065";
    public static final String DATE_TIME_TIMESTAMP_ZONED_DST_WINTER = "2023-11-14T18:31:55Z";
    public static final String DATE_TIME_TIMESTAMP_ZONED_DST_SUMMER = "2023-08-05T09:11:54Z";
    public static final String DATE_TIME_TIMESTAMP_ZONED_MILLISECONDS_DST_WINTER = "2023-11-14T18:31:55.306516999Z";
    public static final String DATE_TIME_TIMESTAMP_ZONED_MILLISECONDS_DST_SUMMER = "2023-08-05T09:11:54.306516999Z";
    public static final boolean GENERATED_BY_DEBTOR = true;
    public static final boolean GENERATED_BY_PAYER = false;
    public static final String PSP_NAME = "name";
    public static final String PSP_LOGO = "logo";
    public static final String PSP_COMPANY = "companyName";
    public static final String PSP_CITY = "city";
    public static final String PSP_POSTAL_CODE = "postalCode";
    public static final String PSP_ADDRESS = "address";
    public static final String PSP_BUILDING_NUMBER = "buildingNumber";
    public static final String PSP_PROVINCE = "province";
    public static final String BRAND_ASSET_URL = "/asset";
    private static final String IUV = "02119891614290410";
    private static final String NOTICE_NUMBER = "valid notice number";
    private static final String BIZ_EVENT_ID = "biz-event-id";
    private static final String MODEL_TYPE_IUV_CODE = "1";
    private static final String MODEL_TYPE_NOTICE_CODE = "2";
    private static final String MODEL_TYPE_NOTICE_TEXT = "codiceAvviso";
    private static final String MODEL_TYPE_IUV_TEXT = "IUV";
    private static final String DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER = "14 novembre 2023, 19:31:55";
    private static final String DATE_TIME_TIMESTAMP_FORMATTED_DST_SUMMER = "05 agosto 2023, 11:11:54";
    private static final String PAGO_PA_CHANNEL_IO = "IO";
    private static final String PAGO_PA_CHANNEL_IO_PAY = "IO-PAY";
    private static final String NOT_PAGO_PA_CHANNEL = "NOT_PAGO_PA_CHANNEL";
    private static final String ID_PA = "idPa";
    private static final String USER_NAME = "user_name";
    private static  final String USER_SURNAME = "user_surname";
    private static final String USER_FORMATTED_FULL_NAME = "user_name user_surname";
    private static final String USER_TAX_CODE = "user tax code";
    public static final String PAGOPA_PA_CHANNEL_ID = "pagopa channel";
    private BuildTemplateServiceImpl buildTemplateService;

    @BeforeEach
    void setUp() throws Exception {
        AtomicReference<BuildTemplateServiceImpl> atomicBuildTemplateService = new AtomicReference<>();
        withEnvironmentVariables().set("BRAND_LOGO_MAP", String.format("{\"%s\":\"%s\"}\n", BRAND, BRAND_ASSET_URL)).execute(() ->
                atomicBuildTemplateService.set(new BuildTemplateServiceImpl())
        );
        buildTemplateService = atomicBuildTemplateService.get();
    }

    @Test
    void mapTemplateAllFieldsSuccessCompleteTemplateAndIOChannel() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_DST_WINTER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessCompleteTemplateAndIOChannelAndDateZonedDSTWinter() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_DST_WINTER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessCompleteTemplateAndIOChannelAndDateZonedDSTSummer() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_DST_SUMMER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_SUMMER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessCompleteTemplateAndIOChannelAndDateZonedWithMillisecondsDSTWinter() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_MILLISECONDS_DST_WINTER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessCompleteTemplateAndIOChannelAndDateZonedWithMillisecondsDSTSummer() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_MILLISECONDS_DST_SUMMER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_SUMMER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessCompleteTemplateAndPagoPaChannelOnTransactionOrigin() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO_PAY)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_DST_WINTER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessCompleteTemplateAndPagoPaChannelOnChannelId() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO_PAY)
                                .build())
                        .info(InfoTransaction.builder().clientId(PAGOPA_PA_CHANNEL_ID).build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_DST_WINTER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessPartialTemplateAndNotPagoPaChannel() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .noticeNumber(NOTICE_NUMBER)
                        .modelType(MODEL_TYPE_NOTICE_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_AMOUNT, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertNull(transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(PAYMENT_TOKEN, transaction.getRrn());
        assertNull(transaction.getPaymentMethod().getName());
        assertNull(transaction.getPaymentMethod().getLogo());
        assertNull(transaction.getPaymentMethod().getAccountHolder());
        assertNull(transaction.getAuthCode());
        assertTrue(transaction.isRequestedByDebtor());
        assertFalse(transaction.isProcessedByPagoPA());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.User user = receiptPdfTemplate.getUser();
        assertNull(user);

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_NOTICE_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(NOTICE_NUMBER, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateWithoutTransactionDetailsSuccess() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_AMOUNT, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertNull(transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(PAYMENT_TOKEN, transaction.getRrn());
        assertNull(transaction.getPaymentMethod().getName());
        assertNull(transaction.getPaymentMethod().getLogo());
        assertNull(transaction.getPaymentMethod().getAccountHolder());
        assertNull(transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertFalse(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateWithoutTransactionDetailsAndPaymentTokenSuccess() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_SUMMER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_SUMMER, transaction.getTimestamp());
        assertEquals(FORMATTED_AMOUNT, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertNull(transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(IUR, transaction.getRrn());
        assertNull(transaction.getPaymentMethod().getName());
        assertNull(transaction.getPaymentMethod().getLogo());
        assertNull(transaction.getPaymentMethod().getAccountHolder());
        assertNull(transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessDebtorFullNameEmpty() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME_INVALID)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_DST_WINTER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertNull(cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessDebtorFullNameWithSpecialChar() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_FULL_NAME_SPECIAL_CHAR)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_DST_WINTER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertEquals(DEBTOR_FULL_NAME_SPECIAL_CHAR_FORMATTED, cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateAllFieldsSuccessDebtorFullNameEqualsFiscalCode() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .idPaymentManager(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtor(Debtor.builder()
                        .fullName(DEBTOR_VALID_CF)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder().fullName(PAYER_FULL_NAME).entityUniqueIdentifierValue(PAYER_VALID_CF).build())
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .paymentToken(PAYMENT_TOKEN)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .fee(FEE_WITH_SINGLE_DIGIT_CENTS)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .IUR(IUR)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder()
                                .info(Info.builder().brand(BRAND).holder(HOLDER_FULL_NAME).build())
                                .onboardingChannel(PAGO_PA_CHANNEL_IO)
                                .build())
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .grandTotal(GRAND_TOTAL_LONG)
                                .amount(AMOUNT_LONG)
                                .fee(FEE_LONG)
                                .rrn(RRN)
                                .numAut(AUTH_CODE)
                                .creationDate(DATE_TIME_TIMESTAMP_ZONED_DST_WINTER)
                                .psp(TransactionPsp.builder()
                                        .businessName(PSP_NAME)
                                        .build())
                                .origin(PAGOPA_PA_CHANNEL_ID)
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
        Receipt receipt = Receipt.builder().eventData(EventData.builder().cart(List.of(CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build()).build();

        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_DEBTOR, receipt)));

        ReceiptPDFTemplate receiptPdfTemplate = atomicReference.get();

        assertNotNull(receiptPdfTemplate);
        assertEquals(BIZ_EVENT_ID, receiptPdfTemplate.getServiceCustomerId());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Transaction transaction = receiptPdfTemplate.getTransaction();
        assertEquals(DATE_TIME_TIMESTAMP_FORMATTED_DST_WINTER, transaction.getTimestamp());
        assertEquals(FORMATTED_GRAND_TOTAL, transaction.getAmount());
        assertEquals(PSP_LOGO, transaction.getPsp().getLogo());
        assertEquals(FORMATTED_FEE, transaction.getPsp().getFee().getAmount());
        assertEquals(PSP_NAME, transaction.getPsp().getName());
        assertEquals(PSP_CITY, transaction.getPsp().getCity());
        assertEquals(PSP_COMPANY, transaction.getPsp().getCompanyName());
        assertEquals(PSP_POSTAL_CODE, transaction.getPsp().getPostalCode());
        assertEquals(PSP_ADDRESS, transaction.getPsp().getAddress());
        assertEquals(PSP_BUILDING_NUMBER, transaction.getPsp().getBuildingNumber());
        assertEquals(PSP_PROVINCE, transaction.getPsp().getProvince());
        assertEquals(RRN, transaction.getRrn());
        assertEquals(BRAND, transaction.getPaymentMethod().getName());
        assertEquals(BRAND_ASSET_URL, transaction.getPaymentMethod().getLogo());
        assertEquals(HOLDER_FULL_NAME, transaction.getPaymentMethod().getAccountHolder());
        assertEquals(AUTH_CODE, transaction.getAuthCode());
        assertEquals(GENERATED_BY_DEBTOR, transaction.isRequestedByDebtor());
        assertTrue(transaction.isProcessedByPagoPA());

        assertNull(receiptPdfTemplate.getUser());

        it.gov.pagopa.receipt.pdf.helpdesk.model.template.Cart cart = receiptPdfTemplate.getCart();
        assertEquals(FORMATTED_AMOUNT, cart.getAmountPartial());
        assertEquals(FORMATTED_AMOUNT, cart.getItems().get(0).getAmount());
        assertNull(cart.getItems().get(0).getDebtor().getFullName());
        assertEquals(DEBTOR_VALID_CF, cart.getItems().get(0).getDebtor().getTaxCode());
        assertEquals(REMITTANCE_INFORMATION, cart.getItems().get(0).getSubject());
        assertEquals(COMPANY_NAME, cart.getItems().get(0).getPayee().getName());
        assertEquals(ID_PA, cart.getItems().get(0).getPayee().getTaxCode());
        assertEquals(MODEL_TYPE_IUV_TEXT, cart.getItems().get(0).getRefNumber().getType());
        assertEquals(IUV, cart.getItems().get(0).getRefNumber().getValue());
    }

    @Test
    void mapTemplateLeastAmountOfInfoSuccessPayer() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .build();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER,
                Receipt.builder().eventData(eventData).build()));
    }

    @Test
    void mapTemplateLeastAmountOfInfoSuccessDebtor() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .build();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> buildTemplateService.buildTemplate(
                event, GENERATED_BY_DEBTOR, Receipt.builder().eventData(eventData).build()));
    }

    @Test
    void mapTemplateSuccessRequestByDebtorTrueWithoutPayerAndUserFiscalCode() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .grandTotal(GRAND_TOTAL_LONG).build()
                        )
                        .build()
                )
                .build();
        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(
                event, GENERATED_BY_DEBTOR, Receipt.builder().eventData(eventData).build())));

        ReceiptPDFTemplate receiptPDFTemplate = atomicReference.get();
        assertTrue(receiptPDFTemplate.getTransaction().isRequestedByDebtor());
    }

    @Test
    void mapTemplateSuccessRequestByDebtorTrueWithPayerFiscalCodeDifferent() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder()
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .grandTotal(GRAND_TOTAL_LONG).build()
                        )
                        .build()
                )
                .build();
        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(
                event, GENERATED_BY_DEBTOR, Receipt.builder().eventData(eventData).build())));

        ReceiptPDFTemplate receiptPDFTemplate = atomicReference.get();
        assertTrue(receiptPDFTemplate.getTransaction().isRequestedByDebtor());
    }

    @Test
    void mapTemplateSuccessRequestByDebtorTrueWithUserFiscalCodeDifferent() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .grandTotal(GRAND_TOTAL_LONG).build()
                        )
                        .user(User.builder().fiscalCode(PAYER_VALID_CF).build())
                        .build()
                )
                .build();
        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event,
                GENERATED_BY_DEBTOR, Receipt.builder().eventData(eventData).build())));

        ReceiptPDFTemplate receiptPDFTemplate = atomicReference.get();
        assertTrue(receiptPDFTemplate.getTransaction().isRequestedByDebtor());
    }

    @Test
    void mapTemplateSuccessRequestByDebtorFalseWithPayerAndUserFiscalCodeEqual() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .grandTotal(GRAND_TOTAL_LONG).build()
                        )
                        .user(User.builder().fiscalCode(DEBTOR_VALID_CF).build())
                        .build()
                )
                .build();
        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(
                event, GENERATED_BY_DEBTOR, Receipt.builder().eventData(eventData).build())));

        ReceiptPDFTemplate receiptPDFTemplate = atomicReference.get();
        assertFalse(receiptPDFTemplate.getTransaction().isRequestedByDebtor());
    }

    @Test
    void mapTemplateSuccessRequestByDebtorFalseWhenGeneratedByPayer() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .grandTotal(GRAND_TOTAL_LONG).build()
                        )
                        .build()
                )
                .build();
        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER,
                Receipt.builder().eventData(eventData).build())));

        ReceiptPDFTemplate receiptPDFTemplate = atomicReference.get();
        assertFalse(receiptPDFTemplate.getTransaction().isRequestedByDebtor());
    }

    @Test
    void mapTemplateSuccessWithUserNameAndSurname() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder()
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .grandTotal(GRAND_TOTAL_LONG).build()
                        )
                        .user(User.builder()
                                .name(USER_NAME)
                                .surname(USER_SURNAME)
                                .build())
                        .build()
                )
                .build();
        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate
                (event, GENERATED_BY_PAYER, Receipt.builder().eventData(eventData).build())));

        ReceiptPDFTemplate receiptPDFTemplate = atomicReference.get();
        assertEquals(USER_FORMATTED_FULL_NAME, receiptPDFTemplate.getUser().getData().getFullName());
    }

    @Test
    void mapTemplateSuccessWithUserTaxCodeFromTransaction() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .payer(Payer.builder()
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .idPA(ID_PA)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .grandTotal(GRAND_TOTAL_LONG).build()
                        )
                        .user(User.builder()
                                .name(USER_NAME)
                                .surname(USER_SURNAME)
                                .fiscalCode(USER_TAX_CODE)
                                .build())
                        .build()
                )
                .build();
        AtomicReference<ReceiptPDFTemplate> atomicReference = new AtomicReference<>();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        assertDoesNotThrow(() -> atomicReference.set(buildTemplateService.buildTemplate(
                event, GENERATED_BY_PAYER, Receipt.builder().eventData(eventData).build())));

        ReceiptPDFTemplate receiptPDFTemplate = atomicReference.get();
        assertEquals(USER_TAX_CODE, receiptPDFTemplate.getUser().getData().getTaxCode());
    }

    @Test
    void mapTemplateNoServiceCustomerIdError() {
        BizEvent event = new BizEvent();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.SERVICE_CUSTOMER_ID), e.getMessage());
    }

    @Test
    void mapTemplateNoTransactionTimestampError() {
        BizEvent event = BizEvent.builder().id(BIZ_EVENT_ID).paymentInfo(PaymentInfo.builder().IUR(IUR).build()).build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_TIMESTAMP), e.getMessage());
    }

    @Test
    void mapTemplateNoTransactionAmountError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_AMOUNT), e.getMessage());
    }

    @Test
    void mapTemplateNoPspError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP), e.getMessage());
    }

    @Test
    void mapTemplateNoPspNameError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp("noName")
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP_NAME), e.getMessage());
    }

    @Test
    void mapTemplateNoPspCompanyNameError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp("noCompanyName")
                        .psp(PSP_NAME)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP_COMPANY_NAME), e.getMessage());
    }

    @Test
    void mapTemplateNoPspAddressError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp("noAddress")
                        .psp(PSP_NAME)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP_ADDRESS), e.getMessage());
    }

    @Test
    void mapTemplateNoPspCityError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp("noCity")
                        .psp(PSP_NAME)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP_CITY), e.getMessage());
    }

    @Test
    void mapTemplateNoPspProvinceError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp("noProvince")
                        .psp(PSP_NAME)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP_PROVINCE), e.getMessage());
    }

    @Test
    void mapTemplateNoPspBuildingNumberError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp("noBuildingNumber")
                        .psp(PSP_NAME)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP_BUILDING_NUMBER), e.getMessage());
    }

    @Test
    void mapTemplateNoPspPostalCodeError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp("noPostalCode")
                        .psp(PSP_NAME)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP_POSTAL_CODE), e.getMessage());
    }

    @Test
    void mapTemplateNoPspLogoError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp("noLogo")
                        .psp(PSP_NAME)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .fee(FEE_LONG)
                                .build())
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_PSP_LOGO), e.getMessage());
    }

    @Test
    void mapTemplateNoRrnError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .idTransaction(ID_TRANSACTION)
                                .build())
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.TRANSACTION_RRN), e.getMessage());
    }

    @Test
    void mapTemplateNoUserDataFullNameError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.USER_DATA_FULL_NAME), e.getMessage());
    }

    @Test
    void mapTemplateNoUserDataTaxCodeError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.USER_DATA_TAX_CODE), e.getMessage());
    }

    @Test
    void mapTemplateNoCartItemRefNumberTypeError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.CART_ITEM_REF_NUMBER_TYPE), e.getMessage());
    }

    @Test
    void mapTemplateNoCartItemRefNumberValueIUVError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.CART_ITEM_REF_NUMBER_VALUE), e.getMessage());
    }

    @Test
    void mapTemplateWrongModelTypeError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .iuv(IUV)
                        .modelType(MODEL_TYPE_NOTICE_CODE)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.CART_ITEM_REF_NUMBER_VALUE), e.getMessage());
    }

    @Test
    void mapTemplateNoCartItemDebtorTaxCodeValueError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER,
                Receipt.builder().eventData(EventData.builder().debtorFiscalCode("DEBTOR_FC").build()).build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.CART_ITEM_DEBTOR_TAX_CODE), e.getMessage());
    }

    @Test
    void mapTemplateNoCartItemPayeeTaxCodeValueError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .amount(AMOUNT_WITHOUT_CENTS)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () -> buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER,
                Receipt.builder().eventData(EventData.builder().debtorFiscalCode("DEBTOR_FC").build()).build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.CART_ITEM_PAYEE_TAX_CODE), e.getMessage());
    }

    @Test
    void mapTemplateNoCartItemAmountValueError() {
        BizEvent event = BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .paymentInfo(PaymentInfo.builder()
                        .IUR(IUR)
                        .paymentDateTime(DATE_TIME_TIMESTAMP_MILLISECONDS_DST_WINTER)
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .build())
                .psp(Psp.builder()
                        .idPsp(ID_PSP)
                        .psp(PSP_NAME)
                        .build())
                .payer(Payer.builder()
                        .fullName(PAYER_FULL_NAME)
                        .entityUniqueIdentifierValue(PAYER_VALID_CF)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .modelType(MODEL_TYPE_IUV_CODE)
                        .iuv(IUV)
                        .build())
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_VALID_CF)
                        .build())
                .creditor(Creditor.builder()
                        .companyName(COMPANY_NAME)
                        .idPA(ID_PA)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .grandTotal(GRAND_TOTAL_LONG).build()
                        )
                        .build()
                )
                .build();
        EventData eventData = EventData.builder().cart(Collections.singletonList(
                CartItem.builder().subject(REMITTANCE_INFORMATION).build())).build();
        TemplateDataMappingException e = assertThrows(TemplateDataMappingException.class, () ->
                buildTemplateService.buildTemplate(event, GENERATED_BY_PAYER, Receipt.builder().eventData(eventData).build()));

        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), e.getStatusCode());
        assertEquals(String.format(TemplateDataField.ERROR_MAPPING_MESSAGE, TemplateDataField.CART_ITEM_AMOUNT), e.getMessage());
    }
}