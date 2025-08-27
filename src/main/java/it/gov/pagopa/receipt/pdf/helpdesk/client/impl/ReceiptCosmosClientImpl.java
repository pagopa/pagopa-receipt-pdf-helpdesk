package it.gov.pagopa.receipt.pdf.helpdesk.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.IoMessageNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Client for the CosmosDB database
 */
public class ReceiptCosmosClientImpl implements ReceiptCosmosClient {

    private static ReceiptCosmosClientImpl instance;

    private final String databaseId = System.getenv().getOrDefault("COSMOS_RECEIPT_DB_NAME", "db");
    private final String containerId = System.getenv().getOrDefault("COSMOS_RECEIPT_CONTAINER_NAME", "receipt");
    private final String containerMessageId = System.getenv().getOrDefault("COSMOS_RECEIPT_MESSAGE_CONTAINER_NAME", "receipts-io-messages-evt");
    private final String containerReceiptErrorId = System.getenv().getOrDefault("COSMOS_RECEIPT_ERROR_CONTAINER_NAME", "receipts-message-errors");

    private final String millisDiff = System.getenv("MAX_DATE_DIFF_MILLIS");
    private final String millisNotifyDif = System.getenv("MAX_DATE_DIFF_NOTIFY_MILLIS");

    private final String numDaysRecoverFailed = System.getenv().getOrDefault("RECOVER_FAILED_MASSIVE_MAX_DAYS", "0");

    private final String numDaysRecoverNotNotified = System.getenv().getOrDefault("RECOVER_NOT_NOTIFIED_MASSIVE_MAX_DAYS", "0");

    private final int recordsLimitRecoverNotNotified = Integer.parseInt(System.getenv().getOrDefault("RECOVER_NOT_NOTIFIED_MASSIVE_MAX_RECORDS", "200"));

    private static final String DOCUMENT_NOT_FOUND_ERR_MSG = "Document not found in the defined container";

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
        throw new ReceiptNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query = String.format("SELECT * FROM c WHERE (c.status = '%s' or c.status = '%s') AND c.inserted_at >= %s",
                ReceiptStatusType.FAILED, ReceiptStatusType.NOT_QUEUE_SENT,
                OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(
                        Long.parseLong(numDaysRecoverFailed)).toInstant().toEpochMilli());

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
        String query =  String.format("SELECT * FROM c WHERE (c.status = '%s' AND c.inserted_at >= %s " +
                        "AND ( %s - c.inserted_at) >= %s)",
                ReceiptStatusType.INSERTED,
                OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(
                        Long.parseLong(numDaysRecoverFailed)).toInstant().toEpochMilli(),
                OffsetDateTime.now().toInstant().toEpochMilli(), millisDiff);

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
        }
        throw new ReceiptNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG);
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
        String query =  String.format("SELECT * FROM c WHERE (c.status = '%s' AND c.generated_at >= %s AND ( %s - c.generated_at) >= %s)",
                ReceiptStatusType.GENERATED,
                OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(
                        Long.parseLong(numDaysRecoverNotNotified)).toInstant().toEpochMilli(),
                OffsetDateTime.now().toInstant().toEpochMilli(), millisNotifyDif);

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
        // String query = String.format("SELECT * FROM c WHERE c.status = '%s' AND c.generated_at >= %s OFFSET 0 LIMIT %s",
        //         ReceiptStatusType.IO_ERROR_TO_NOTIFY,
        //         OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(
        //                 Long.parseLong(numDaysRecoverNotNotified)).toInstant().toEpochMilli(),
        //         recordsLimitRecoverNotNotified
        // );
        String query = String.format("SELECT * FROM c WHERE c.status = '%s' AND c.generated_at >= %s",
                ReceiptStatusType.IO_ERROR_TO_NOTIFY,
                OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(
                        Long.parseLong(numDaysRecoverNotNotified)).toInstant().toEpochMilli()
        );

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class)
                .iterableByPage(continuationToken,pageSize);
    }

    /**
     * Retrieve receipt document from CosmosDB database
     *
     * @param messageId IO Message id
     * @return io message document
     * @throws IoMessageNotFoundException in case no receipt has been found with the given messageId
     */
    @Override
    public IOMessage getIoMessage(String messageId) throws IoMessageNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerMessageId);

        //Build query
        String query = String.format("SELECT * FROM c WHERE c.messageId = '%s'", messageId);

        //Query the container
        CosmosPagedIterable<IOMessage> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), IOMessage.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        }
        throw new IoMessageNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG);
    }
    
    @Override
    public CosmosItemResponse<Object> deleteReceipt(Receipt receipt)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        return cosmosContainer.deleteItem(
        	    receipt.getId(),
        	    new PartitionKey(receipt.getId()),
        	    new CosmosItemRequestOptions()
        	);
    }

}
