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
        String query = String.format("SELECT * FROM c WHERE c.eventId = '%s'", eventId);

        //Query the container
        CosmosPagedIterable<Receipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        }
        throw new ReceiptNotFoundException("Document not found in the defined container");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query = String.format("SELECT * FROM c WHERE c.status = '%s' or c.status = '%s'",
                ReceiptStatusType.FAILED, ReceiptStatusType.NOT_QUEUE_SENT);

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class)
                .iterableByPage(continuationToken,pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getInsertedReceiptDocuments(String continuationToken, Integer pageSize) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query =  String.format("SELECT * FROM c WHERE (c.status= = '%s' AND ( %s - c.inserted_at) >= %s)",
                ReceiptStatusType.INSERTED, OffsetDateTime.now().toInstant().toEpochMilli(), millisDiff);

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
    public Iterable<FeedResponse<Receipt>> getGeneratedReceiptDocuments(String continuationToken, Integer pageSize)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query =  String.format("SELECT * FROM c WHERE (c.status= = '%s' AND ( %s - c.generated_at) >= %s)",
                ReceiptStatusType.GENERATED, OffsetDateTime.now().toInstant().toEpochMilli(), millisDiff);

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class)
                .iterableByPage(continuationToken,pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getIOErrorToNotifyReceiptDocuments(String continuationToken, Integer pageSize)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query = String.format("SELECT * FROM c WHERE c.status = '%s'", ReceiptStatusType.IO_ERROR_TO_NOTIFY);

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class)
                .iterableByPage(continuationToken,pageSize);
    }
}
