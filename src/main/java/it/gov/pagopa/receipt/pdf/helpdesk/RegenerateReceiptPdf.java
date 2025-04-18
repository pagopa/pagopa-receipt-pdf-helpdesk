package it.gov.pagopa.receipt.pdf.helpdesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import io.micrometer.core.instrument.util.StringUtils;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.GenerateReceiptPdfServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils.isFromAuthenticatedOrigin;
import static it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils.isReceiptStatusValid;
import static it.gov.pagopa.receipt.pdf.helpdesk.utils.GenerateReceiptUtils.*;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RegenerateReceiptPdf {

    private static final Logger logger = LoggerFactory.getLogger(RegenerateReceiptPdf.class);
    
    private static final String TEMPLATE_PREFIX = "pagopa-ricevuta";
    private static final String PAYER_TEMPLATE_SUFFIX = "p";
    private static final String DEBTOR_TEMPLATE_SUFFIX = "d";
    
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosClient receiptCosmosClient;
    private final GenerateReceiptPdfService generateReceiptPdfService;

    private final BizEventToReceiptService bizEventToReceiptService;

    public RegenerateReceiptPdf(){
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
        this.generateReceiptPdfService = new GenerateReceiptPdfServiceImpl();
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
    }

    RegenerateReceiptPdf(BizEventCosmosClient bizEventCosmosClient,
                         ReceiptCosmosClient receiptCosmosClient,
                         GenerateReceiptPdfService generateReceiptPdfService,
                         BizEventToReceiptService bizEventToReceiptService){
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosClient = receiptCosmosClient;
        this.generateReceiptPdfService = generateReceiptPdfService;
        this.bizEventToReceiptService = bizEventToReceiptService;
    }


    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("RegenerateReceiptFunc")
    public HttpResponseMessage run (
            @HttpTrigger(name = "RegenerateReceiptPdfFuncTrigger",
                    methods = {HttpMethod.POST},
                    route = "receipts/{bizeventid}/regenerate-receipt-pdf",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("bizeventid") String eventId,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
					containerName = "receipts",
					connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentdb,
            final ExecutionContext context) {

        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (eventId != null) {
        		
        	BizEvent bizEvent = null;
        	List<BizEvent> listBizEvent = new ArrayList<>();
        	boolean isCart = Boolean.FALSE;

            try {

                isCart = Boolean.parseBoolean(request.getQueryParameters().getOrDefault(
                        "isCart", "false"));

                if (isCart) {
                    listBizEvent = bizEventToReceiptService.getCartBizEvents(eventId);
                    bizEvent = listBizEvent.get(0);
                } else {
                    bizEvent = bizEventCosmosClient.getBizEventDocument(eventId);
                }
                
                //Try to Retrieve receipt's data from CosmosDB
                Receipt receipt = getReceipt(context, bizEvent, receiptCosmosClient, logger);
                //If the receipt exists --> regeneration of the PDF file for the receipt
                return this.generateAndSavePDF(request, documentdb, context, isCart, bizEvent, listBizEvent, receipt);
                
            } catch (ReceiptNotFoundException | BizEventNotFoundException exception) {
            	if (exception.getClass().equals(ReceiptNotFoundException.class)) {
            		//If the receipt does not exist --> regeneration of the receipt and the related PDF file
            		HttpResponseMessage response = this.generateAndSaveReceipt(request, context, bizEvent);
            		if (HttpStatus.OK.equals(response.getStatus())) {
            			Receipt receipt = (Receipt) response.getBody();
            			return this.generateAndSavePDF(request, documentdb, context, isCart, bizEvent, listBizEvent, receipt);
            		} else {
            			return response;
            		}

            	} else if (exception.getClass().equals(BizEventNotFoundException.class)) {
                	logger.error(exception.getMessage(), exception);
                	return request
                			.createResponseBuilder(HttpStatus.BAD_REQUEST)
                			.body(ProblemJson.builder()
                					.title(HttpStatus.BAD_REQUEST.name())
                					.detail("BizEvent not found with event-id " + eventId)
                					.status(HttpStatus.BAD_REQUEST.value())
                					.build())
                			.build();
                }
            }
        }

        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(ProblemJson.builder()
                        .title(HttpStatus.BAD_REQUEST.name())
                        .detail("Missing valid eventId paramater")
                        .status(HttpStatus.BAD_REQUEST.value())
                        .build())
                .build();

    }

    private HttpResponseMessage generateAndSavePDF(HttpRequestMessage<Optional<String>> request, OutputBinding<Receipt> documentdb,
    		final ExecutionContext context, boolean isCart, BizEvent bizEvent, List<BizEvent> listBizEvent,
    		Receipt receipt) {

    	boolean success = false;
    	
    	HttpResponseMessage errorResponse = request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
		.body(ProblemJson.builder()
				.title(HttpStatus.INTERNAL_SERVER_ERROR.name())
				.detail("Receipt could not be updated with the new attachments")
				.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
				.build())
		.build();
    	
    	//Verify receipt status
    	if (receipt.getEventData() != null) {

    		logger.debug("[{}] Generating pdf for Receipt with id {} and bizEvent with id {}",
    				context.getFunctionName(),
    				receipt.getId(),
    				bizEvent.getId());
    		
    		
    		boolean isToUpdateMetadata = !isHasAllAttachments(receipt);
    		RegenerateReceiptPdf.checkOrCreateAttachments(receipt);

    		//Generate and save PDF
    		PdfGeneration pdfGeneration;
    		Path workingDirPath = Path.of("-");
    		try {
    			workingDirPath = createWorkingDirectory();

    			pdfGeneration = generateReceiptPdfService.generateReceipts(receipt, isCart ?
    					listBizEvent : Collections.singletonList(bizEvent), workingDirPath);

    			//Verify PDF generation success
    			success = generateReceiptPdfService.verifyAndUpdateReceipt(receipt, pdfGeneration);
    			
    			this.updateReceiptInfo(documentdb, isCart, isToUpdateMetadata, bizEvent, listBizEvent, receipt, pdfGeneration);

    		} catch (IOException e) {
                logger.error("[{}] IOException while generating the receipt with id {}: {}", 
                		context.getFunctionName(), receipt.getId(), e.getMessage(), e);
                errorResponse = request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail("Unexpected error while managing the receipt file")
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
            } catch (Exception e) {
            	logger.error("[{}] Generic Exception while generating the receipt with id {}: {}", 
                		context.getFunctionName(), receipt.getId(), e.getMessage(), e);
    			errorResponse = request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
    					.body(ProblemJson.builder()
    							.title(HttpStatus.INTERNAL_SERVER_ERROR.name())
    							.detail("Error during receipt generation: " + e.getMessage())
    							.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
    							.build())
    					.build();
    		} finally {
    			deleteTempFolder(workingDirPath, logger);
    		}

    	}
    	
    	return success ?
    			request.createResponseBuilder(HttpStatus.OK)
    			.body("OK")
    			.build() :
    			errorResponse;
    }
    
    private static void checkOrCreateAttachments(Receipt receipt) {
    	final String blobNameFormat = "%s-%s-%s-%s";
    	final String blobNameDateFormat = "yyMMdd";
        String debtorCF = receipt.getEventData().getDebtorFiscalCode();
        String payerCF = receipt.getEventData().getPayerFiscalCode();
        if (payerCF != null) {
        	 createPayerAndDebtorMdAttach(receipt, blobNameFormat, blobNameDateFormat, debtorCF, payerCF);
        } else {
        	if (!"ANONIMO".equals(debtorCF) && !receiptMetadataExist(receipt.getMdAttach())) {
        		String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern(blobNameDateFormat));
                String blobName = String.format(blobNameFormat, TEMPLATE_PREFIX, dateFormatted, receipt.getEventId(), DEBTOR_TEMPLATE_SUFFIX);
       		    receipt.setMdAttach(ReceiptMetadata.builder().name(blobName).build());
        	}
        }
    }

	private static void createPayerAndDebtorMdAttach(Receipt receipt, final String blobNameFormat,
			final String blobNameDateFormat, String debtorCF, String payerCF) {
		if (payerCF.equals(debtorCF) && !receiptMetadataExist(receipt.getMdAttach())) {
			 String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern(blobNameDateFormat));
		     String blobName = String.format(blobNameFormat, TEMPLATE_PREFIX, dateFormatted, receipt.getEventId(), PAYER_TEMPLATE_SUFFIX);
			 receipt.setMdAttach(ReceiptMetadata.builder().name(blobName).build());
		 } else { 
			 if (!receiptMetadataExist(receipt.getMdAttachPayer())){
				 String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern(blobNameDateFormat));
				 String blobName = String.format(blobNameFormat, TEMPLATE_PREFIX, dateFormatted, receipt.getEventId(), PAYER_TEMPLATE_SUFFIX);
				 receipt.setMdAttachPayer(ReceiptMetadata.builder().name(blobName).build());
			 }
			 if (!"ANONIMO".equals(debtorCF) && !receiptMetadataExist(receipt.getMdAttach())) {
		 		String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern(blobNameDateFormat));
		        String blobName = String.format(blobNameFormat, TEMPLATE_PREFIX, dateFormatted, receipt.getEventId(), DEBTOR_TEMPLATE_SUFFIX);
				receipt.setMdAttach(ReceiptMetadata.builder().name(blobName).build());
		 	}
		 }
	}
    
	private void updateReceiptInfo(OutputBinding<Receipt> documentdb, boolean isCart, boolean isToUpdateMetadata, BizEvent bizEvent,
			List<BizEvent> listBizEvent, Receipt receipt, PdfGeneration pdfGeneration) throws PDVTokenizerException, JsonProcessingException {
		
		if (receipt.getEventData().getDebtorFiscalCode() == null ||
				(receipt.getEventData().getPayerFiscalCode() == null
				&& isFromAuthenticatedOrigin(bizEvent))) {
			BizEventToReceiptUtils.tokenizeReceipt(bizEventToReceiptService, isCart ?
					listBizEvent : Collections.singletonList(bizEvent), receipt);
			documentdb.setValue(receipt);
		}

		if (receipt.getEventData().getCart() == null || receipt.getEventData().getCart()
				.isEmpty() || receipt.getEventData().getCart().get(0).getSubject() == null) {
			receipt.getEventData().setCart(BizEventToReceiptUtils.getCartItems(bizEvent));
			documentdb.setValue(receipt);
		}
		
		if (isToUpdateMetadata) {
			this.updateReceiptStatus(receipt, pdfGeneration);
			documentdb.setValue(receipt);
		}
	}

	private void updateReceiptStatus(Receipt receipt, PdfGeneration pdfGeneration) {
		PdfMetadata debtorMetadata = pdfGeneration.getDebtorMetadata() != null ? pdfGeneration.getDebtorMetadata() : PdfMetadata.builder().build();
		PdfMetadata payerMetadata = pdfGeneration.getPayerMetadata() != null ? pdfGeneration.getPayerMetadata() : PdfMetadata.builder().build();
		boolean pdfSuccessfullyGen =  StringUtils.isEmpty(debtorMetadata.getErrorMessage()) && StringUtils.isEmpty(payerMetadata.getErrorMessage());
		if (pdfSuccessfullyGen) {
			receipt.setReasonErr(null);
			receipt.setReasonErrPayer(null);
			receipt.setNumRetry(0);
			receipt.setNotificationNumRetry(0);
			receipt.setStatus(ReceiptStatusType.IO_NOTIFIED);
			receipt.setNotified_at(System.currentTimeMillis());
		} else {
			receipt.setReasonErr(StringUtils.isNotEmpty(debtorMetadata.getErrorMessage()) ? 
					ReasonError.builder().code(debtorMetadata.getStatusCode()).message(debtorMetadata.getErrorMessage()).build() : null);
			receipt.setReasonErrPayer(StringUtils.isNotEmpty(payerMetadata.getErrorMessage()) ? 
					ReasonError.builder().code(payerMetadata.getStatusCode()).message(payerMetadata.getErrorMessage()).build() : null);
			receipt.setStatus(ReceiptStatusType.FAILED);
		}
	}
    
    private HttpResponseMessage generateAndSaveReceipt(HttpRequestMessage<Optional<String>> request, final ExecutionContext context, BizEvent bizEvent) {
    	// check if is a valid biz event 
        if (bizEvent == null || BizEventToReceiptUtils.isBizEventInvalid(bizEvent, context, logger)) {
        	return request
        			.createResponseBuilder(HttpStatus.BAD_REQUEST)
        			.body(ProblemJson.builder()
        					.title(HttpStatus.BAD_REQUEST.name())
        					.detail(bizEvent!=null?"BizEvent with id "+bizEvent.getId()+" is not valid":"The BizEvent object is NULL")
        					.status(HttpStatus.BAD_REQUEST.value())
        					.build())
        			.build();
        }

        Receipt receipt = BizEventToReceiptUtils.createReceipt(bizEvent, bizEventToReceiptService, logger);
        
        Integer totalNotice = BizEventToReceiptUtils.getTotalNotice(bizEvent, context, logger);

        if (totalNotice == 1) {

            logger.debug("[{}] function called at {} for event with id {} and status {} and isCart {}",
                    context.getFunctionName(), LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus(), totalNotice > 1);

            if (BizEventToReceiptUtils.isReceiptStatusValid(receipt)) {
                // Add receipt to items to be saved on CosmosDB
                bizEventToReceiptService.handleSaveReceipt(receipt, ReceiptStatusType.IO_NOTIFIED);
            }
            
            if (!isReceiptStatusValid(receipt)) {
                logger.error("[{}] Failed to process receipt with id {} and eventId {}: fail to save receipt",
                        context.getFunctionName(), receipt.getId(), receipt.getEventId());
                return request
                        .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail("Failed to save receipt")
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
            }

        } else if (totalNotice > 1) {
            // TODO cart management: future developments to be defined
        	return request
                    .createResponseBuilder(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.UNPROCESSABLE_ENTITY.name())
                            .detail("Failed to save receipt: cart type receipt management not yet available")
                            .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                            .build())
                    .build();
        }
        
        return request.createResponseBuilder(HttpStatus.OK)
    			.body(receipt)
    			.build();
    }
    
    private static boolean receiptMetadataExist(ReceiptMetadata receiptMetadata) {
        return receiptMetadata != null
                && receiptMetadata.getUrl() != null
                && receiptMetadata.getName() != null
                && !receiptMetadata.getUrl().isEmpty()
                && !receiptMetadata.getName().isEmpty();
    }
    
    private static boolean isHasAllAttachments(Receipt receipt) {
        String debtorCF = receipt.getEventData().getDebtorFiscalCode();
        String payerCF = receipt.getEventData().getPayerFiscalCode();
        boolean hasAllAttachments;
        if (payerCF == null) {
          hasAllAttachments = receipt.getMdAttach() != null && receipt.getMdAttach().getUrl() != null;
        } else if (debtorCF.equals(payerCF)) {
            hasAllAttachments = receipt.getMdAttach() != null && receipt.getMdAttach().getUrl() != null;
        } else {
            hasAllAttachments = receipt.getMdAttach() != null && receipt.getMdAttach().getUrl() != null &&
                    receipt.getMdAttachPayer() != null && receipt.getMdAttachPayer().getUrl() != null;
        }
        return hasAllAttachments;
    }

}