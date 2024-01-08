package it.gov.pagopa.receipt.pdf.helpdesk.client.impl;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.helpdesk.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public class CartReceiptsCosmosClientImpl implements CartReceiptsCosmosClient {

    private static CartReceiptsCosmosClientImpl instance;
    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String cartForReceiptContainerName = System.getenv("CART_FOR_RECEIPT_CONTAINER_NAME");

    private final String millisDiff = System.getenv("MAX_DATE_DIFF_CART_MILLIS");

    private final String numDaysCartNotSent = System.getenv().getOrDefault("RECOVER_CART_MASSIVE_MAX_DAYS", "0");


    private final CosmosClient cosmosClient;

    private CartReceiptsCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .buildClient();
    }

    public CartReceiptsCosmosClientImpl(CosmosClient cosmosClient) {
        this.cosmosClient = cosmosClient;
    }

    public static CartReceiptsCosmosClientImpl getInstance() {
        if (instance == null) {
            instance = new CartReceiptsCosmosClientImpl();
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
    @Override
    public CartForReceipt getCartItem(String eventId) throws CartNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);

        //Build query
        String query = "SELECT * FROM c WHERE c.id = " + "'" + eventId + "'";

        //Query the container
        CosmosPagedIterable<CartForReceipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), CartForReceipt.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        } else {
            throw new CartNotFoundException("Document not found in the defined container");
        }

    }

    /**
     * Save Cart For Receipt on CosmosDB database
     *
     * @param receipt Cart Data to save
     * @return cart-to-receipts documents
     */
    @Override
    public CosmosItemResponse<CartForReceipt> saveCart(CartForReceipt receipt)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);
        return cosmosContainer.createItem(receipt);
    }

    @Override
    public Iterable<FeedResponse<CartForReceipt>> getFailedCarts(String continuationToken, int size) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);

        //Build query
        String query = String.format("SELECT * FROM c WHERE (c.status = '%s') AND c._ts >= %s",
                CartStatusType.FAILED,
                OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(
                        Long.parseLong(numDaysCartNotSent)).toInstant().toEpochMilli());

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), CartForReceipt.class)
                .iterableByPage(continuationToken,size);
    }

    @Override
    public Iterable<FeedResponse<CartForReceipt>> getInsertedCarts(String continuationToken, int size) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);

        //Build query
        String query =  String.format("SELECT * FROM c WHERE (c.status = '%s' AND c._ts >= %s " +
                        "AND ( %s - c._ts) >= %s)",
                ReceiptStatusType.INSERTED,
                OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(
                        Long.parseLong(numDaysCartNotSent)).toInstant().toEpochMilli(),
                OffsetDateTime.now().toInstant().toEpochMilli(), millisDiff);

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), CartForReceipt.class)
                .iterableByPage(continuationToken,100);
    }

}
