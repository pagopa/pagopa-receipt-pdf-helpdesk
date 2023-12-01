package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;

public class ReceiptCosmosServiceImpl implements ReceiptCosmosService {

    private final ReceiptCosmosClient receiptCosmosClient;

    public ReceiptCosmosServiceImpl() {
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    ReceiptCosmosServiceImpl(ReceiptCosmosClient receiptCosmosClient) {
        this.receiptCosmosClient = receiptCosmosClient;
    }

    @Override
    public Receipt getReceipt(String eventId) throws ReceiptNotFoundException {
        Receipt receipt;
        try {
            receipt = this.receiptCosmosClient.getReceiptDocument(eventId);
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("Receipt not found with the biz-event id %s", eventId);
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = String.format("Receipt retrieved with the biz-event id %s is null", eventId);
            throw new ReceiptNotFoundException(errorMsg);
        }
        return receipt;
    }

    @Override
    public Iterable<FeedResponse<Receipt>> getNotNotifiedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    ) {
        if (statusType == null) {
            throw new IllegalArgumentException("at least one param must be true");
        }
        if (statusType.equals(ReceiptStatusType.IO_ERROR_TO_NOTIFY)) {
            return this.receiptCosmosClient.getIOErrorToNotifyReceiptDocuments(continuationToken, pageSize);
        }
        if (statusType.equals(ReceiptStatusType.GENERATED)) {
            return this.receiptCosmosClient.getGeneratedReceiptDocuments(continuationToken, pageSize);
        }
        String errMsg = String.format("Unexpected status for not notified query: %s", statusType);
        throw new IllegalStateException(errMsg);
    }
}