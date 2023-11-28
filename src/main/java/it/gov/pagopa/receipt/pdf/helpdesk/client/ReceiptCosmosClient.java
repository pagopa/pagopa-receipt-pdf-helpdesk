package it.gov.pagopa.receipt.pdf.helpdesk.client;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;

public interface ReceiptCosmosClient {

    Receipt getReceiptDocument(String eventId) throws ReceiptNotFoundException;

    Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize);

    CosmosItemResponse<Receipt> saveReceipts(Receipt receipt);

    ReceiptError getReceiptError(String bizEventId) throws  ReceiptNotFoundException;

    Iterable<FeedResponse<ReceiptError>> getToReviewReceiptsError(String continuationToken, Integer pageSize);

    /**
     * Retrieve the receipt documents with status {@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}
     * or {@link ReceiptStatusType#GENERATED} from Cosmos database
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @param ioErrorToNotifyStatus true if the receipts must be in {@link ReceiptStatusType#IO_ERROR_TO_NOTIFY} status, false otherwise
     * @param generatedStatus true if the receipts must be in {@link ReceiptStatusType#GENERATED} status, false otherwise
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getNotNotifiedReceiptDocuments(
            String continuationToken,
            Integer pageSize,
            boolean ioErrorToNotifyStatus,
            boolean generatedStatus
    );
}
