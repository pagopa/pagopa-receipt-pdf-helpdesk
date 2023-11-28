package it.gov.pagopa.receipt.pdf.helpdesk.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;

import java.time.OffsetDateTime;

/**
 * Client for the CosmosDB database
 */
public class ReceiptCosmosClientImpl implements ReceiptCosmosClient {

    private static ReceiptCosmosClientImpl instance;

    private final String databaseId = System.getenv().getOrDefault("COSMOS_RECEIPT_DB_NAME", "db");
    private final String containerId = System.getenv().getOrDefault("COSMOS_RECEIPT_CONTAINER_NAME", "receipt");
    private final String containerReceiptErrorId = System.getenv().getOrDefault("COSMOS_RECEIPT_ERROR_CONTAINER_NAME", "receipts-message-errors");

    private final String millisDiff = System.getenv("MAX_DATE_DIFF_MILLIS");

    private final CosmosClient cosmosClient;

    private ReceiptCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .buildClient();
    }

    public ReceiptCosmosClientImpl(CosmosClient cosmosClient) {
        this.cosmosClient = cosmosClient;
    }

    public static ReceiptCosmosClientImpl getInstance() {
        if (instance == null) {
            instance = new ReceiptCosmosClientImpl();
        }
        return instance;
    }

    /**
     * Retrieve receipt document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return receipt document
     * @throws ReceiptNotFoundException in case no receipt has been found with the given idEvent
     */
    public Receipt getReceiptDocument(String eventId) throws ReceiptNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query = "SELECT * FROM c WHERE c.eventId = " + "'" + eventId + "'";

        //Query the container
        CosmosPagedIterable<Receipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        } else {
            throw new ReceiptNotFoundException("Document not found in the defined container");
        }
    }

    /**
     * Retrieve failed receipt documents from CosmosDB database
     *
     * @param continuationToken Paged query continuation token
     * @return receipt documents
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query = "SELECT * FROM c WHERE c.status = 'FAILED' or c.status = 'NOT_QUEUE_SENT' or " +
                "( c.status= = 'INSERTED' AND ( " + OffsetDateTime.now().toInstant().toEpochMilli() +
                " - c.inserted_at) >= " + millisDiff + " )";

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class)
                .iterableByPage(continuationToken,pageSize);
    }

    /**
     * Save Receipts on CosmosDB database
     *
     * @param receipt Receipts to save
     * @return receipt documents
     */
    @Override
    public CosmosItemResponse<Receipt> saveReceipts(Receipt receipt)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        return cosmosContainer.createItem(receipt);
    }

    /**
     * Retrieve receiptError document from CosmosDB database
     *
     * @param bizEventId BizEvent ID
     * @return ReceiptError found
     * @throws ReceiptNotFoundException If the document isn't found
     */
    @Override
    public ReceiptError getReceiptError(String bizEventId) throws  ReceiptNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerReceiptErrorId);

        //Build query
        String query = "SELECT * FROM c WHERE c.bizEventId = " + "'" + bizEventId + "'";

        //Query the container
        CosmosPagedIterable<ReceiptError> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), ReceiptError.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        } else {
            throw new ReceiptNotFoundException("Document not found in the defined container");
        }
    }

    /**
     * Retrieve receiptError documents to-review from CosmosDB database
     * @param continuationToken Paged query continuation token
     * @param pageSize Page size
     * @return receiptError documents
     */
    @Override
    public Iterable<FeedResponse<ReceiptError>> getToReviewReceiptsError(String continuationToken, Integer pageSize){
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerReceiptErrorId);

        //Build query
        String query = String.format("SELECT * FROM c WHERE c.status = '%s'", ReceiptErrorStatusType.TO_REVIEW);

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), ReceiptError.class)
                .iterableByPage(continuationToken,pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getNotNotifiedReceiptDocuments(
            String continuationToken,
            Integer pageSize,
            boolean ioErrorToNotifyStatus,
            boolean generatedStatus
    )  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        if (!ioErrorToNotifyStatus && !generatedStatus) {
            throw new IllegalArgumentException("at least one param must be true");
        }

        //Build query
        String query = buildQuery(ioErrorToNotifyStatus, generatedStatus);

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class)
                .iterableByPage(continuationToken,pageSize);
    }

    private String buildQuery(boolean ioErrorToNotifyStatus, boolean generatedStatus) {
        String query = "SELECT *CosmosPagedIterable<Receipt> FROM c WHERE ";
        String ioErrorNotifyParam = String.format("c.status = '%s'", ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        String generatedParam =  String.format("(c.status= = '%s' AND ( %s - c.inserted_at) >= %s)",
                ReceiptStatusType.GENERATED, OffsetDateTime.now().toInstant().toEpochMilli(), millisDiff);

        if (ioErrorToNotifyStatus && generatedStatus) {
           return query.concat(ioErrorNotifyParam).concat(" AND ").concat(generatedParam);
        }
        if (ioErrorToNotifyStatus) {
            return query.concat(ioErrorNotifyParam);
        }
        return query.concat(generatedParam);
    }
}
