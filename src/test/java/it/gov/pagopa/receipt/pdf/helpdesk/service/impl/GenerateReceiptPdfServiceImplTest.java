package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import it.gov.pagopa.receipt.pdf.helpdesk.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.*;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.TemplateDataMappingException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.helpdesk.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.GenerateReceiptPdfService;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static it.gov.pagopa.receipt.pdf.helpdesk.service.impl.GenerateReceiptPdfServiceImpl.ALREADY_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class GenerateReceiptPdfServiceImplTest {

    private static final String VALID_CF_DEBTOR = "JHNDOE00A01F205N";
    private static final String VALID_CF_PAYER = "PLMGHN00A01F406L";
    private static final String BIZ_EVENT_ID = "062-a330-4210-9c67-465b7d641aVS";
    private static final String DEBTOR_DOCUMENT_NAME = "debtorDocumentName";
    private static final String DEBTOR_DOCUMENT_URL = "debtorDocumentUrl";
    private static final String PAYER_DOCUMENT_NAME = "payerDocumentName";
    private static final String PAYER_DOCUMENT_URL = "payerDocumentUrl";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String RECEIPT_METADATA_ORIGINAL_URL = "originalUrl";
    private static final String RECEIPT_METADATA_ORIGINAL_NAME = "originalName";

    private static File outputPdfDebtor;
    private static File tempDirectoryDebtor;
    private static File outputPdfPayer;
    private static File tempDirectoryPayer;

    private PdfEngineClient pdfEngineClientMock;
    private ReceiptBlobClient receiptBlobClientMock;
    private BuildTemplateService buildTemplateServiceMock;
    private GenerateReceiptPdfService sut;

    @BeforeEach
    void setUp() throws IOException {
        pdfEngineClientMock = mock(PdfEngineClient.class);
        receiptBlobClientMock= mock(ReceiptBlobClient.class);
        buildTemplateServiceMock = mock(BuildTemplateService.class);

        sut = spy(new GenerateReceiptPdfServiceImpl(pdfEngineClientMock, receiptBlobClientMock, buildTemplateServiceMock));
        Path basePath = Path.of("src/test/resources");
        tempDirectoryDebtor = Files.createTempDirectory(basePath, "tempDebtor").toFile();
        outputPdfDebtor = File.createTempFile("outputDebtor", ".tmp", tempDirectoryDebtor);
        tempDirectoryPayer = Files.createTempDirectory(basePath, "tempPayer").toFile();
        outputPdfPayer = File.createTempFile("outputPayer", ".tmp", tempDirectoryPayer);
    }

    @AfterEach
    public void teardown() throws IOException {
        if(tempDirectoryDebtor.exists()){
            FileUtils.deleteDirectory(tempDirectoryDebtor);
        }
        if(tempDirectoryPayer.exists()){
            FileUtils.deleteDirectory(tempDirectoryPayer);
        }
        assertFalse(tempDirectoryDebtor.exists());
        assertFalse(outputPdfDebtor.exists());
        assertFalse(tempDirectoryPayer.exists());
        assertFalse(outputPdfPayer.exists());
    }

    @Test
    void generateReceiptsPayerNullWithSuccess() throws Exception {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(true);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doReturn(getPdfEngineResponse(SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, Collections.singletonList(bizEventOnly),Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(SC_OK, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());
        verify(pdfEngineClientMock).generatePDF(any(), any());
        verify(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
    }

    @Test
    void generateReceiptsSameDebtorPayerWithSuccess() throws Exception {
        Receipt receiptOnly = getReceiptWithDebtorPayer(VALID_CF_DEBTOR, true, true);
        BizEvent bizEventOnly = getBizEventWithDebtorPayer(VALID_CF_DEBTOR);

        doReturn(getPdfEngineResponse(SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, Collections.singletonList(bizEventOnly),Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(SC_OK, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());
        verify(pdfEngineClientMock).generatePDF(any(), any());
        verify(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
    }

    @Test
    void generateReceiptsDifferentDebtorPayerWithSuccess() throws Exception {
        Receipt receiptOnly = getReceiptWithDebtorPayer(VALID_CF_PAYER, true, true);
        BizEvent bizEventOnly = getBizEventWithDebtorPayer(VALID_CF_PAYER);

        doReturn(getPdfEngineResponse(SC_OK, outputPdfDebtor.getPath()),
                getPdfEngineResponse(SC_OK, outputPdfPayer.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()),
                getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.CREATED.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, Collections.singletonList(bizEventOnly),Path.of("/tmp"));


        assertNotNull(pdfGeneration);
        assertFalse(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(SC_OK, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNotNull(pdfGeneration.getPayerMetadata());
        assertNull(pdfGeneration.getPayerMetadata().getErrorMessage());
        assertNotNull(pdfGeneration.getPayerMetadata().getDocumentName());
        assertNotNull(pdfGeneration.getPayerMetadata().getDocumentUrl());
        assertEquals(SC_OK, pdfGeneration.getPayerMetadata().getStatusCode());

        verify(buildTemplateServiceMock, times(2)).buildTemplate(any(), anyBoolean(), any());
        verify(pdfEngineClientMock, times(2)).generatePDF(any(), any());
        verify(receiptBlobClientMock, times(2)).savePdfToBlobStorage(any(), anyString());
    }

    @Test
    void generateReceiptsPayerNullFailPDFEngineCallReturn500() throws Exception {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(true);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doReturn(getPdfEngineResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, ""))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, Collections.singletonList(bizEventOnly),Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNotNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());
        verify(pdfEngineClientMock).generatePDF(any(), any());
        verify(receiptBlobClientMock, never()).savePdfToBlobStorage(any(), anyString());
    }

    @Test
    void generateReceiptsPayerNullFailBuildTemplateData() throws Exception {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(true);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doThrow(new TemplateDataMappingException("error message", ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode()))
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, Collections.singletonList(bizEventOnly),Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNotNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode(), pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());
        verify(pdfEngineClientMock, never()).generatePDF(any(), any());
        verify(receiptBlobClientMock, never()).savePdfToBlobStorage(any(), anyString());
    }

    @Test
    void generateReceiptsPayerNullFailSaveToBlobStorageThrowsException() throws Exception {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(true);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doReturn(getPdfEngineResponse(SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doThrow(RuntimeException.class).when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, Collections.singletonList(bizEventOnly),Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull(pdfGeneration.getDebtorMetadata());
        assertNotNull(pdfGeneration.getDebtorMetadata().getErrorMessage());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentName());
        assertNull(pdfGeneration.getDebtorMetadata().getDocumentUrl());
        assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), pdfGeneration.getDebtorMetadata().getStatusCode());
        assertNull(pdfGeneration.getPayerMetadata());

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());
        verify(pdfEngineClientMock).generatePDF(any(), any());
        verify(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
    }

    @Test
    void generateReceiptsPayerNullFailSaveToBlobStorageReturn500() throws Exception {
        Receipt receiptOnly = getReceiptWithOnlyDebtor(true);
        BizEvent bizEventOnly = getBizEventWithOnlyDebtor();

        doReturn(getPdfEngineResponse(SC_OK, outputPdfDebtor.getPath()))
                .when(pdfEngineClientMock).generatePDF(any(), any());
        doReturn(getBlobStorageResponse(com.microsoft.azure.functions.HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .when(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
        doReturn(new ReceiptPDFTemplate())
                .when(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());

        PdfGeneration pdfGeneration = sut.generateReceipts(receiptOnly, Collections.singletonList(bizEventOnly),Path.of("/tmp"));

        assertNotNull(pdfGeneration);
        assertTrue(pdfGeneration.isGenerateOnlyDebtor());
        assertNotNull((pdfGeneration.getDebtorMetadata()));
        assertNotNull((pdfGeneration.getDebtorMetadata().getErrorMessage()));
        assertNull((pdfGeneration.getDebtorMetadata().getDocumentName()));
        assertNull((pdfGeneration.getDebtorMetadata().getDocumentUrl()));
        assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), (pdfGeneration.getDebtorMetadata().getStatusCode()));
        assertNull((pdfGeneration.getPayerMetadata()));

        verify(buildTemplateServiceMock).buildTemplate(any(), anyBoolean(), any());
        verify(pdfEngineClientMock).generatePDF(any(), any());
        verify(receiptBlobClientMock).savePdfToBlobStorage(any(), anyString());
    }

    @Test
    void verifyPayerNullOrSameDebtorPayerWithSuccess() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(true, true);
        receipt.setEventData(EventData.builder().debtorFiscalCode("DEBTOR_FC").build());

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .generateOnlyDebtor(true)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttach().getUrl());
    }

    @Test
    void verifyDifferentDebtorPayerWithSuccess() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(true, true);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .documentName(PAYER_DOCUMENT_NAME)
                        .documentUrl(PAYER_DOCUMENT_URL)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getMdAttachPayer().getUrl());
        assertNotNull(receipt.getMdAttachPayer().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttachPayer().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttachPayer().getUrl());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    void verifyPayerNullOrSameDebtorPayerFailMetadataNull() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .generateOnlyDebtor(true)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertFalse(result);
        assertNull(receipt.getMdAttach());
        assertNull(receipt.getMdAttachPayer());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    void verifyPayerNullOrSameDebtorPayerAlreadyCreatedSuccess() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(true, false);
        receipt.setEventData(EventData.builder().debtorFiscalCode("DEBTOR_FC").build());

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .build())
                .generateOnlyDebtor(true)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttach().getUrl());
        assertNull(receipt.getMdAttachPayer());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    void verifyPayerNullOrSameDebtorPayerFailReceiptGenerationInError() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(false, false);
        receipt.setEventData(EventData.builder().debtorFiscalCode("DEBTOR_FC").build());

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .generateOnlyDebtor(true)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

    }

    @Test
    void verifyPayerNullOrSameDebtorPayerFailThrowsReceiptGenerationNotToRetryException() {
        Receipt receipt = buildReceiptForVerify(false, false);
        receipt.setEventData(EventData.builder().debtorFiscalCode("DEBTOR_FC").build());

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .generateOnlyDebtor(true)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

    }

    @Test
    void verifyDifferentDebtorPayerAndPayerAlreadyGeneratedSuccess() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(true, true);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertTrue(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttachPayer());
        assertNotNull(receipt.getMdAttachPayer().getUrl());
        assertNotNull(receipt.getMdAttachPayer().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttachPayer().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttachPayer().getUrl());
        assertNull(receipt.getReasonErr());
        assertNull(receipt.getReasonErrPayer());
    }

    @Test
    void verifyDifferentDebtorPayerFailDebtorGenerationInError() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(false, false);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .documentName(PAYER_DOCUMENT_NAME)
                        .documentUrl(PAYER_DOCUMENT_URL)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

    }

    @Test
    void verifyDifferentDebtorPayerFailPayerGenerationInError() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(true, true);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .documentName(DEBTOR_DOCUMENT_NAME)
                        .documentUrl(DEBTOR_DOCUMENT_URL)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

    }

    @Test
    void verifyDifferentDebtorPayerFailGenerationInErrorForBoth() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(true, true);

        String errorMessagePayer = "error message payer";
        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_BAD_REQUEST)
                        .errorMessage(errorMessagePayer)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

    }

    @Test
    void verifyDifferentDebtorPayerFailPayerReceiptMetadataNull() throws ReceiptGenerationNotToRetryException {
        Receipt receipt = buildReceiptForVerify(true, true);

        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(SC_OK)
                        .documentName(RECEIPT_METADATA_ORIGINAL_NAME)
                        .documentUrl(RECEIPT_METADATA_ORIGINAL_URL)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        boolean result = sut.verifyAndUpdateReceipt(receipt, pdfGeneration);

        assertFalse(result);
        assertNotNull(receipt.getMdAttach());
        assertNotNull(receipt.getMdAttach().getUrl());
        assertNotNull(receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_NAME, receipt.getMdAttach().getName());
        assertEquals(RECEIPT_METADATA_ORIGINAL_URL, receipt.getMdAttach().getUrl());
    }

    @Test
    void verifyDifferentDebtorPayerFailThrowsReceiptGenerationNotToRetryException() {
        Receipt receipt = buildReceiptForVerify(true, true);

        String errorMessagePayer = "error message payer";
        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(HttpStatus.SC_BAD_REQUEST)
                        .errorMessage(errorMessagePayer)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

    }

    @Test
    void verifyDifferentDebtorPayerFailBothThrowsReceiptGenerationNotToRetryException() {
        Receipt receipt = buildReceiptForVerify(true, true);

        String errorMessagePayer = "error message payer";
        PdfGeneration pdfGeneration = PdfGeneration.builder()
                .debtorMetadata(PdfMetadata.builder()
                        .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                        .errorMessage(ERROR_MESSAGE)
                        .build())
                .payerMetadata(PdfMetadata.builder()
                        .statusCode(ReasonErrorCode.ERROR_TEMPLATE_PDF.getCode())
                        .errorMessage(errorMessagePayer)
                        .build())
                .generateOnlyDebtor(false)
                .build();

        assertThrows(ReceiptGenerationNotToRetryException.class, () -> sut.verifyAndUpdateReceipt(receipt, pdfGeneration));

    }

    private Receipt buildReceiptForVerify(boolean debtorAlreadyCreated, boolean payerAlreadyCreated) {
        return Receipt.builder()
                .id("id")
                .mdAttach(buildMetadata(debtorAlreadyCreated))
                .mdAttachPayer(buildMetadata(payerAlreadyCreated))
                .numRetry(0)
                .generated_at(1L)
                .inserted_at(1L)
                .notified_at(1L)
                .build();
    }

    private BlobStorageResponse getBlobStorageResponse(int status) {
        BlobStorageResponse blobStorageResponse = new BlobStorageResponse();
        blobStorageResponse.setStatusCode(status);
        if (status == com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
            blobStorageResponse.setDocumentName("document");
            blobStorageResponse.setDocumentUrl("url");
        }
        return blobStorageResponse;
    }

    private PdfEngineResponse getPdfEngineResponse(int status, String pdfPath) {
        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();
        pdfEngineResponse.setTempPdfPath(pdfPath);
        if (status != SC_OK) {
            pdfEngineResponse.setErrorMessage("error");
        }
        pdfEngineResponse.setStatusCode(status);
        return pdfEngineResponse;
    }

    private BizEvent getBizEventWithOnlyDebtor() {
        return getBizEventWithDebtorPayer(null);

    }
    private BizEvent getBizEventWithDebtorPayer(String payer) {
        if (payer == null) {
            return getBizEvent(null);
        }
        return getBizEvent(Payer.builder().fullName("John Doe").entityUniqueIdentifierValue(VALID_CF_DEBTOR).build());

    }
    private BizEvent getBizEvent(Payer payer) {
        return BizEvent.builder()
                .id(BIZ_EVENT_ID)
                .debtorPosition(DebtorPosition.builder()
                        .iuv("02119891614290410")
                        .modelType("2")
                        .build())
                .creditor(Creditor.builder()
                        .companyName("PA paolo")
                        .officeName("office PA")
                        .build())
                .psp(Psp.builder()
                        .idPsp("60000000001")
                        .psp("PSP Paolo")
                        .build())
                .debtor(Debtor.builder()
                        .fullName("John Doe")
                        .entityUniqueIdentifierValue(VALID_CF_DEBTOR)
                        .build())
                .payer(payer)
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime("2023-04-12T16:21:39.022486")
                        .paymentToken("9a9bad2caf604b86a339476373c659b0")
                        .amount("7000")
                        .fee("200")
                        .remittanceInformation("TARI 2021")
                        .IUR("IUR")
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .wallet(WalletItem.builder().info(Info.builder().brand("MASTER").build()).pagoPa(false).favourite(false).build())
                        .transaction(Transaction.builder()
                                .idTransaction("1")
                                .grandTotal(0L)
                                .amount(7000L)
                                .fee(200L)
                                .rrn("rrn")
                                .authorizationCode("authCode")
                                .creationDate("2023-10-14T00:03:27Z")
                                .psp(TransactionPsp.builder()
                                        .businessName("Nexi")
                                        .serviceName("Nexi")
                                        .build())
                                .build())
                        .build())
                .eventStatus(BizEventStatusType.DONE)
                .build();
    }

    private Receipt getReceiptWithOnlyDebtor(boolean alreadyCreated) {
        return getReceiptWithDebtorPayer(null, alreadyCreated, false);
    }
    private Receipt getReceiptWithDebtorPayer(String payer, boolean debtorAlreadyCreated, boolean payerAlreadyCreated) {
        return getReceipt(getEventData(payer), buildMetadata(debtorAlreadyCreated), buildMetadata(payerAlreadyCreated));
    }

    private Receipt getReceipt(EventData eventData, ReceiptMetadata metadataD, ReceiptMetadata metadataP) {
        return Receipt.builder()
                .eventData(eventData)
                .eventId(BIZ_EVENT_ID)
                .mdAttach(metadataD)
                .mdAttachPayer(metadataP)
                .status(ReceiptStatusType.INSERTED)
                .numRetry(0)
                .generated_at(1L)
                .inserted_at(1L)
                .notified_at(1L)
                .build();
    }

    private EventData getEventData(String payer) {
        return EventData.builder()
                .debtorFiscalCode(VALID_CF_DEBTOR)
                .payerFiscalCode(payer)
                .build();
    }

    private ReceiptMetadata buildMetadata(boolean build) {
        if (build) {
            return ReceiptMetadata.builder()
                    .name(RECEIPT_METADATA_ORIGINAL_NAME)
                    .url(RECEIPT_METADATA_ORIGINAL_URL)
                    .build();
        }
        return null;
    }
}