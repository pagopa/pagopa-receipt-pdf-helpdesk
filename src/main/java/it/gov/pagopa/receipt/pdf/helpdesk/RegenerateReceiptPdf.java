package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.GenerateReceiptPdfServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.GenerateReceiptUtils.*;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RegenerateReceiptPdf {

    private final Logger logger = LoggerFactory.getLogger(RegenerateReceiptPdf.class);
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosClient receiptCosmosClient;
    private final GenerateReceiptPdfService generateReceiptPdfService;

    public RegenerateReceiptPdf(){
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
        this.generateReceiptPdfService = new GenerateReceiptPdfServiceImpl();
    }

//    RegenerateReceiptPdf(BizEventCosmosClient bizEventCosmosClient,
//                         ReceiptCosmosClient receiptCosmosClient,
//                         GenerateReceiptPdfService generateReceiptPdfService){
//        this.bizEventCosmosClient = bizEventCosmosClient;
//        this.receiptCosmosClient = receiptCosmosClient;
//        this.generateReceiptPdfService = generateReceiptPdfService;
//    }


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
            final ExecutionContext context) {

        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (eventId != null) {

            try {

                BizEvent bizEvent = bizEventCosmosClient.getBizEventDocument(eventId);

                //Retrieve receipt's data from CosmosDB
                Receipt receipt = getReceipt(context, bizEvent, receiptCosmosClient, logger);

                //Verify receipt status
                if (receipt.getEventData() != null
//                        && isHasAllAttachments(receipt)
                ) {

                    logger.info("[{}] Generating pdf for Receipt with id {} and bizEvent with id {}",
                            context.getFunctionName(),
                            receipt.getId(),
                            bizEvent.getId());

                    //Generate and save PDF
                    PdfGeneration pdfGeneration;
                    Path workingDirPath = createWorkingDirectory();
                    try {
                        //pdfGeneration = generateReceiptPdfService.generateReceipts(receipt, bizEvent, workingDirPath);

                        //Verify PDF generation success
                        boolean success = true;
                        //success = generateReceiptPdfService.verifyAndUpdateReceipt(receipt, pdfGeneration);

                        return success ?
                                request.createResponseBuilder(HttpStatus.OK)
                                        .body("OK").build() :
                                request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ProblemJson.builder()
                                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                                .detail("Receipt could not be updated with the new attachments")
                                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                                .build())
                                        .build();

                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                        request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
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

            } catch (ReceiptNotFoundException | BizEventNotFoundException exception) {
                logger.error(exception.getMessage(), exception);
                String message = "Missing required informations";
                if (exception.getClass().equals(ReceiptNotFoundException.class)) {
                    message = "Receipt not found with event-id " + eventId;
                } else if (exception.getClass().equals(BizEventNotFoundException.class)) {
                    message = "BizEvent not found with event-id " + eventId;
                }
                return request
                        .createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.BAD_REQUEST.name())
                                .detail(message)
                                .status(HttpStatus.BAD_REQUEST.value())
                                .build())
                        .build();
            }
            catch (IOException e) {
                logger.error(e.getMessage(), e);
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail("Unexpected error while managing the receipt file")
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
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