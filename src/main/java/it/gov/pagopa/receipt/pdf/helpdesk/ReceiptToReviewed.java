package it.gov.pagopa.receipt.pdf.helpdesk;

import com.azure.cosmos.models.FeedResponse;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ReceiptToReviewedRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;


/**
 * Azure Functions with Azure Http trigger.
 */
public class ReceiptToReviewed {

    private final ReceiptCosmosClient receiptCosmosClient;

    public ReceiptToReviewed(){
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    ReceiptToReviewed(
            ReceiptCosmosClient receiptCosmosClient){
        this.receiptCosmosClient = receiptCosmosClient;
    }


    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("ReceiptToReviewed")
    public HttpResponseMessage run (
            @HttpTrigger(name = "ReceiptToReviewedFunction",
                    methods = {HttpMethod.PUT},
                    route = "toReviewed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<ReceiptToReviewedRequest>> request,
            @CosmosDBOutput(
                    name = "ReceiptErrorDatastore",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<ReceiptError>> documentdb,
            final ExecutionContext context) {

        List<ReceiptError> receiptList = new ArrayList<>();

        try {

            ReceiptToReviewedRequest receiptSupportRequest = request.getBody().isPresent() ? request.getBody().get() : new ReceiptToReviewedRequest();

            if (receiptSupportRequest.getEventId() != null) {

                ReceiptError receiptError = receiptCosmosClient.getReceiptError(
                        receiptSupportRequest.getEventId());
                receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);
                receiptList.add(receiptError);
            } else {

                String continuationToken = null;

                do {

                    Iterable<FeedResponse<ReceiptError>> feedResponseIterator =
                            receiptCosmosClient.getToReviewReceiptsError(continuationToken, 100);

                    for (FeedResponse<ReceiptError> page : feedResponseIterator) {

                        for (ReceiptError receiptError : page.getResults()) {
                            receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);
                            receiptList.add(receiptError);
                        }
                        continuationToken = page.getContinuationToken();
                    }

                } while (continuationToken != null);
            }


            documentdb.setValue(receiptList);
            return request.createResponseBuilder(HttpStatus.OK)
                    .body("OK")
                    .build();

        } catch (NoSuchElementException | ReceiptNotFoundException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

}
