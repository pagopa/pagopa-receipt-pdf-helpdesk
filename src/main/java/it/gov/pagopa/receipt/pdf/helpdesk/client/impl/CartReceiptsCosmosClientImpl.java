package it.gov.pagopa.receipt.pdf.helpdesk.client.impl;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.helpdesk.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public class CartReceiptsCosmosClientImpl implements CartReceiptsCosmosClient {

    private static CartReceiptsCosmosClientImpl instance;
    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String cartForReceiptContainerName = System.getenv("COSMOS_RECEIPT_CART_CONTAINER_NAME");

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
     * {@inheritDoc}
     */
    @Override
    public CartForReceipt getCartItem(String cartId) throws CartNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);

        //Build query
        String query = String.format("SELECT * FROM c WHERE c.id = '%s'", cartId);

        //Query the container
        CosmosPagedIterable<CartForReceipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), CartForReceipt.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        }
        throw new CartNotFoundException("Document not found in the defined container");
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
        String query = String.format("SELECT * FROM c WHERE (c.status = '%s') AND c.inserted_at >= %s",
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
        String query =  String.format("SELECT * FROM c WHERE (c.status = '%s' AND c.inserted_at >= %s " +
                        "AND ( %s - c.inserted_at) >= %s)",
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
