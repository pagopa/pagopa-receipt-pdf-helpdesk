package it.gov.pagopa.receipt.pdf.helpdesk.service;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;

/**
 * Service that handle the input and output for the {@link ReceiptCosmosClient}
 */
public interface ReceiptCosmosService {

    /**
     * Retrieve the receipt with the provided biz-event id
     *
     * @param eventId the biz-event id
     * @return the receipt
     * @throws ReceiptNotFoundException if the receipt was not found or the retrieved receipt is null
     */
    Receipt getReceipt(String eventId) throws ReceiptNotFoundException;

    /**
     * Retrieve the not notified receipt with the provided {@link ReceiptStatusType} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @param statusType the status of the receipts
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getNotNotifiedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    );

    /**
     * Retrieve the failed receipt with the provided {@link ReceiptStatusType} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @param statusType the status of the receipts
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getFailedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    );
}
