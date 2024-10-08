package it.gov.pagopa.receipt.pdf.helpdesk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceipt.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosService receiptCosmosService;

    public RecoverFailedReceipt(){
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    RecoverFailedReceipt(BizEventToReceiptService bizEventToReceiptService,
                         BizEventCosmosClient bizEventCosmosClient,
                         ReceiptCosmosService receiptCosmosService){
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers the receipt with the specified biz event id that has the following status:
     * - ({@link ReceiptStatusType#INSERTED})
     * - ({@link ReceiptStatusType#FAILED})
     * - ({@link ReceiptStatusType#NOT_QUEUE_SENT})
     * <p>
     * It creates the receipts if not exist and send on queue the event in order to proceed with the receipt generation.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverFailedReceipt")
    public HttpResponseMessage run (
            @HttpTrigger(name = "RecoverFailedReceiptTrigger",
                    methods = {HttpMethod.POST},
                    route = "receipts/{event-id}/recover-failed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("event-id") String eventId,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentdb,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (eventId == null || eventId.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid biz-event id")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        Boolean isCart = Boolean.parseBoolean(request.getQueryParameters().getOrDefault(
                "isCart", "false"));

        try {
            Receipt receipt = BizEventToReceiptUtils.getEvent(eventId, context, this.bizEventToReceiptService,
                    this.bizEventCosmosClient, this.receiptCosmosService, null, logger, isCart);

            documentdb.setValue(receipt);
            String responseMsg = String.format("Receipt with eventId %s recovered", eventId);
            return request.createResponseBuilder(HttpStatus.OK)
                    .body(responseMsg)
                    .build();

        } catch (BizEventNotFoundException exception) {
            String msg = String.format("Unable to retrieve the biz-event with id %s", eventId);
            logger.error(msg, exception);
            return request
                    .createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.NOT_FOUND.name())
                            .detail(msg)
                            .status(HttpStatus.NOT_FOUND.value())
                            .build())
                    .build();
        } catch (PDVTokenizerException | JsonProcessingException e) {
            logger.error(e.getMessage(), e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(e.getMessage())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }
    }
}