package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import it.gov.pagopa.receipt.pdf.helpdesk.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.GeneratePDFException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDFReceiptGenerationException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptGenerationNotToRetryException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.SavePDFToBlobException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.helpdesk.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.helpdesk.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.model.template.ReceiptPDFTemplate;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BuildTemplateService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.ObjectMapperUtils;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.apache.http.HttpStatus.SC_OK;

public class GenerateReceiptPdfServiceImpl implements GenerateReceiptPdfService {

    private final Logger logger = LoggerFactory.getLogger(GenerateReceiptPdfServiceImpl.class);

    private final PdfEngineClient pdfEngineClient;
    private final ReceiptBlobClient receiptBlobClient;
    private final BuildTemplateService buildTemplateService;

    public GenerateReceiptPdfServiceImpl() {
        this.pdfEngineClient = PdfEngineClientImpl.getInstance();
        this.receiptBlobClient = ReceiptBlobClientImpl.getInstance();
        this.buildTemplateService = new BuildTemplateServiceImpl();
    }

    GenerateReceiptPdfServiceImpl(PdfEngineClient pdfEngineClient, ReceiptBlobClient receiptBlobClient, BuildTemplateService buildTemplateService) {
        this.pdfEngineClient = pdfEngineClient;
        this.receiptBlobClient = receiptBlobClient;
        this.buildTemplateService = buildTemplateService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PdfGeneration generateReceipts(Receipt receipt, List<BizEvent> listOfBizEvents, Path workingDirPath) {
    	
    	String debtorCF = receipt.getEventData().getDebtorFiscalCode();
        String payerCF = receipt.getEventData().getPayerFiscalCode();
    	
        return pdfGeneration(receipt, listOfBizEvents, workingDirPath, debtorCF, payerCF);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyAndUpdateReceipt(Receipt receipt, PdfGeneration pdfGeneration) throws ReceiptGenerationNotToRetryException {
        PdfMetadata debtorMetadata = pdfGeneration.getDebtorMetadata();
        boolean result = true;

        if (receipt.getEventData() != null && !"ANONIMO".equals(receipt.getEventData().getDebtorFiscalCode())) {

            if (debtorMetadata == null) {
                logger.error("Unexpected result for debtor pdf receipt generation. Receipt id {}", receipt.getId());
                return false;
            }
            
            if (debtorMetadata.getStatusCode() == HttpStatus.SC_OK && 
            		(receipt.getMdAttach() == null || receipt.getMdAttach().getName() == null || receipt.getMdAttach().getUrl() == null)) {
                ReceiptMetadata receiptMetadata = new ReceiptMetadata();
                receiptMetadata.setName(debtorMetadata.getDocumentName());
                receiptMetadata.setUrl(debtorMetadata.getDocumentUrl());

                receipt.setMdAttach(receiptMetadata);
            } 
            
            if (pdfGeneration.isGenerateOnlyDebtor()) {
                if (debtorMetadata.getStatusCode() != SC_OK) {
                    String errMsg = String.format("Debtor receipt generation fail with status %s", debtorMetadata.getStatusCode());
                    throw new ReceiptGenerationNotToRetryException(errMsg);
                }
                return result;
            }   
        }
        
        PdfMetadata payerMetadata = pdfGeneration.getPayerMetadata();
        if (payerMetadata == null) {
            logger.error("Unexpected result for payer pdf receipt generation. Receipt id {}", receipt.getId());
            return false;
        }

        if (payerMetadata.getStatusCode() == HttpStatus.SC_OK && 
        		(receipt.getMdAttachPayer() == null || receipt.getMdAttachPayer().getName() == null || receipt.getMdAttachPayer().getUrl() == null)) {
            ReceiptMetadata receiptMetadata = new ReceiptMetadata();
            receiptMetadata.setName(payerMetadata.getDocumentName());
            receiptMetadata.setUrl(payerMetadata.getDocumentUrl());

            receipt.setMdAttachPayer(receiptMetadata);
        } 

        if (debtorMetadata.getStatusCode() != SC_OK
                || payerMetadata.getStatusCode() != SC_OK) {
            String errMsg = String.format("Receipt generation fail for debtor (status: %s) and/or payer (status: %s)",
                    debtorMetadata.getStatusCode(), payerMetadata.getStatusCode());
            throw new ReceiptGenerationNotToRetryException(errMsg);
        }
        return result;
    }
    
    private PdfGeneration pdfGeneration(Receipt receipt, List<BizEvent> listOfBizEvents, Path workingDirPath,
    		String debtorCF, String payerCF) {

    	PdfGeneration pdfGeneration = new PdfGeneration();

    	if (payerCF != null) {
    		if (payerCF.equals(debtorCF)) {
    			pdfGeneration.setGenerateOnlyDebtor(true);
    			//Generate debtor's complete PDF
    			PdfMetadata generationResult = generateAndSavePDFReceipt(listOfBizEvents, receipt, receipt.getMdAttach().getName(), false, workingDirPath);
    			pdfGeneration.setDebtorMetadata(generationResult);
    			return pdfGeneration;
    		}
    		//Generate payer's complete PDF
    		PdfMetadata generationResult = generateAndSavePDFReceipt(listOfBizEvents, receipt, receipt.getMdAttachPayer().getName(), false, workingDirPath);
    		pdfGeneration.setPayerMetadata(generationResult);

    	} else {
    		pdfGeneration.setGenerateOnlyDebtor(true);
    	}
    	//Generate debtor's partial PDF
    	if (!"ANONIMO".equals(debtorCF)) {
    		PdfMetadata generationResult = generateAndSavePDFReceipt(listOfBizEvents, receipt, receipt.getMdAttach().getName(), true, workingDirPath);
    		pdfGeneration.setDebtorMetadata(generationResult);
    	}

    	return pdfGeneration;
    }

    private PdfMetadata generateAndSavePDFReceipt(List<BizEvent> listOfBizEvents, Receipt receipt, String blobName, boolean isGeneratingDebtor, Path workingDirPath) {
        try {
            ReceiptPDFTemplate template = buildTemplateService.buildTemplate(listOfBizEvents, isGeneratingDebtor, receipt);
            PdfEngineResponse pdfEngineResponse = generatePDFReceipt(template, workingDirPath);
            return saveToBlobStorage(pdfEngineResponse, blobName);
        } catch (PDFReceiptGenerationException e) {
            logger.error("An error occurred when generating or saving the PDF receipt with eventId {}. Error: {}", receipt.getEventId(), e.getMessage(), e);
            return PdfMetadata.builder().statusCode(e.getStatusCode()).errorMessage(e.getMessage()).build();
        }
    }

    private PdfMetadata saveToBlobStorage(PdfEngineResponse pdfEngineResponse, String blobName) throws SavePDFToBlobException {
        String tempPdfPath = pdfEngineResponse.getTempPdfPath();

        BlobStorageResponse blobStorageResponse;
        //Save to Blob Storage
        try (BufferedInputStream pdfStream = new BufferedInputStream(new FileInputStream(tempPdfPath))) {
            blobStorageResponse = receiptBlobClient.savePdfToBlobStorage(pdfStream, blobName);
        } catch (Exception e) {
            throw new SavePDFToBlobException("Error saving pdf to blob storage", ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), e);
        }

        if (blobStorageResponse.getStatusCode() != com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
            String errMsg = String.format("Error saving pdf to blob storage, storage responded with status %s",
                    blobStorageResponse.getStatusCode());
            throw new SavePDFToBlobException(errMsg, ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
        }

        //Update PDF metadata
        return PdfMetadata.builder()
                .documentName(blobStorageResponse.getDocumentName())
                .documentUrl(blobStorageResponse.getDocumentUrl())
                .statusCode(SC_OK)
                .build();
    }

    private PdfEngineResponse generatePDFReceipt(ReceiptPDFTemplate template, Path workingDirPath) throws PDFReceiptGenerationException {
        PdfEngineRequest request = new PdfEngineRequest();

        URL templateStream = GenerateReceiptPdfServiceImpl.class.getClassLoader().getResource("template.zip");
        //Build the request
        request.setTemplate(templateStream);
        request.setData(parseTemplateDataToString(template));
        request.setApplySignature(false);

        PdfEngineResponse pdfEngineResponse = pdfEngineClient.generatePDF(request, workingDirPath);

        if (pdfEngineResponse.getStatusCode() != SC_OK) {
            String errMsg = String.format("PDF-Engine response KO (%s): %s", pdfEngineResponse.getStatusCode(), pdfEngineResponse.getErrorMessage());
            throw new GeneratePDFException(errMsg, pdfEngineResponse.getStatusCode());
        }

        return pdfEngineResponse;
    }

    private String parseTemplateDataToString(ReceiptPDFTemplate template) throws GeneratePDFException {
        try {
            return Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(template));
        } catch (Exception e) {
            throw new GeneratePDFException("Error preparing input data for receipt PDF template", ReasonErrorCode.ERROR_PDF_ENGINE.getCode(), e);
        }
    }
    
}
