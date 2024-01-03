package it.gov.pagopa.receipt.pdf.helpdesk.client;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.IoMessageNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;

public interface ReceiptCosmosClient {

    Receipt getReceiptDocument(String eventId) throws ReceiptNotFoundException;
    /**
     * Retrieve the failed receipt documents with {@link ReceiptStatusType#INSERTED} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getInsertedReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Retrieve the failed receipt documents with {@link ReceiptStatusType#FAILED} or
     * {@link ReceiptStatusType#NOT_QUEUE_SENT} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize);

    CosmosItemResponse<Receipt> saveReceipts(Receipt receipt);

    ReceiptError getReceiptError(String bizEventId) throws  ReceiptNotFoundException;

    Iterable<FeedResponse<ReceiptError>> getToReviewReceiptsError(String continuationToken, Integer pageSize);

    /**
     * Retrieve the not notified receipt documents with {@link ReceiptStatusType#GENERATED}
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getGeneratedReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Retrieve the receipt not notified documents with {@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getIOErrorToNotifyReceiptDocuments(String continuationToken, Integer pageSize);

    IOMessage getIoMessage(String messageId) throws IoMessageNotFoundException;

    /**
     * Retrieve the cart with the provided id from Cosmos
     *
     * @param cartId the cart id
     * @return the cart
     * @throws CartNotFoundException if no cart was found in the container
     */
    CartForReceipt getCartDocument(String cartId) throws CartNotFoundException;
}
