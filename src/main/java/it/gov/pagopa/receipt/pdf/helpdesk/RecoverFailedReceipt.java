package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.FeedResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ReceiptFailedRecoveryRequest;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceipt.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosClient receiptCosmosClient;

    public RecoverFailedReceipt(){
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    RecoverFailedReceipt(BizEventToReceiptService bizEventToReceiptService,
                         BizEventCosmosClient bizEventCosmosClient,
                         ReceiptCosmosClient receiptCosmosClient){
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosClient = receiptCosmosClient;
    }


    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("RecoverFailedReceipt")
    public HttpResponseMessage run (
            @HttpTrigger(name = "RecoverFailedReceiptTrigger",
                    methods = {HttpMethod.PUT},
                    route = "recoverFailed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<ReceiptFailedRecoveryRequest>> request,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            final ExecutionContext context) {

        List<Receipt> receiptList = new ArrayList<>();

        try {

            ReceiptFailedRecoveryRequest receiptFailedRecoveryRequest = request.getBody().get();

            if (receiptFailedRecoveryRequest.getEventId() != null) {

                getEvent(receiptFailedRecoveryRequest.getEventId(), context, bizEventToReceiptService, receiptList,
                        bizEventCosmosClient, receiptCosmosClient, null);

            } else {

                String continuationToken = null;

                do {

                    Iterable<FeedResponse<Receipt>> feedResponseIterator =
                            receiptCosmosClient.getFailedReceiptDocuments(continuationToken, 100);

                    for (FeedResponse<Receipt> page : feedResponseIterator) {

                        for (Receipt receipt : page.getResults()) {
                            try {
                                getEvent(receipt.getEventId(), context, bizEventToReceiptService, receiptList,
                                        bizEventCosmosClient, receiptCosmosClient, receipt);
                            } catch (Exception e) {
                                logger.error(e.getMessage(), e);
                            }
                        }

                        continuationToken = page.getContinuationToken();

                    }

                } while (continuationToken != null);

            }


            documentdb.setValue(receiptList);
            return request.createResponseBuilder(HttpStatus.OK)
                    .body("OK")
                    .build();

        } catch (NoSuchElementException | ReceiptNotFoundException | BizEventNotFoundException exception) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .build();
        } catch (PDVTokenizerException | JsonProcessingException e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private void getEvent(String eventId, ExecutionContext context,
                          BizEventToReceiptService bizEventToReceiptService,
                          List<Receipt> receiptList, BizEventCosmosClient bizEventCosmosClient,
                          ReceiptCosmosClient receiptCosmosClient, Receipt receipt)
            throws BizEventNotFoundException, ReceiptNotFoundException, PDVTokenizerException, JsonProcessingException {

            BizEvent bizEvent = bizEventCosmosClient.getBizEventDocument(
                    eventId);

            if (!BizEventToReceiptUtils.isBizEventInvalid(bizEvent, context, logger)) {

                if (receipt == null) {
                    try {
                        receipt = receiptCosmosClient.getReceiptDocument(
                                eventId);
                    } catch (ReceiptNotFoundException e) {
                        receipt = BizEventToReceiptUtils.createReceipt(bizEvent,
                                bizEventToReceiptService, logger);
                        receipt.setStatus(ReceiptStatusType.FAILED);
                    }
                }

                if (receipt != null && (
                        receipt.getStatus().equals(ReceiptStatusType.FAILED) ||
                        receipt.getStatus().equals(ReceiptStatusType.INSERTED) ||
                        receipt.getStatus().equals(ReceiptStatusType.NOT_QUEUE_SENT)
                )) {
                    if (receipt.getEventData() == null || receipt.getEventData().getDebtorFiscalCode() == null) {
                        BizEventToReceiptUtils.tokenizeReceipt(bizEventToReceiptService, bizEvent, receipt);
                    }
                    bizEventToReceiptService.handleSendMessageToQueue(bizEvent, receipt);
                    if(receipt.getStatus() != ReceiptStatusType.NOT_QUEUE_SENT){
                        receipt.setStatus(ReceiptStatusType.INSERTED);
                        receipt.setInsertedAt(System.currentTimeMillis());
                        receipt.setReasonErr(null);
                        receipt.setReasonErrPayer(null);
                    }
                    receiptList.add(receipt);
                }

            }

    }

}