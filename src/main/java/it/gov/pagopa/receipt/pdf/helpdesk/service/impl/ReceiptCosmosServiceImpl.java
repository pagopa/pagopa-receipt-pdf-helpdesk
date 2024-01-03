package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.IoMessageNotFoundException;
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getNotNotifiedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    ) {
        if (statusType == null) {
            throw new IllegalArgumentException("at least one status must be specified");
        }
        if (statusType.equals(ReceiptStatusType.IO_ERROR_TO_NOTIFY)) {
            return this.receiptCosmosClient.getIOErrorToNotifyReceiptDocuments(continuationToken, pageSize);
        }
        if (statusType.equals(ReceiptStatusType.GENERATED)) {
            return this.receiptCosmosClient.getGeneratedReceiptDocuments(continuationToken, pageSize);
        }
        String errMsg = String.format("Unexpected status for retrieving not notified receipt: %s", statusType);
        throw new IllegalStateException(errMsg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getFailedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    ) {
        if (statusType == null) {
            throw new IllegalArgumentException("at least one status must be specified");
        }
        if (statusType.equals(ReceiptStatusType.FAILED) || statusType.equals(ReceiptStatusType.NOT_QUEUE_SENT)) {
            return this.receiptCosmosClient.getFailedReceiptDocuments(continuationToken, pageSize);
        }
        if (statusType.equals(ReceiptStatusType.INSERTED)) {
            return this.receiptCosmosClient.getInsertedReceiptDocuments(continuationToken, pageSize);
        }
        String errMsg = String.format("Unexpected status for retrieving failed receipt: %s", statusType);
        throw new IllegalStateException(errMsg);
    }

    @Override
    public IOMessage getReceiptMessage(String messageId) throws IoMessageNotFoundException {
        IOMessage message;
        try {
            message = this.receiptCosmosClient.getIoMessage(messageId);
        } catch (IoMessageNotFoundException e) {
            String errorMsg = String.format("Receipt Message to IO not found with the message id %s", messageId);
            throw new IoMessageNotFoundException(errorMsg, e);
        }

        if (message == null) {
            String errorMsg = String.format("Receipt retrieved with the message id %s is null", messageId);
            throw new IoMessageNotFoundException(errorMsg);
        }
        return message;
    }

    @Override
    public CartForReceipt getCart(String cartId) throws CartNotFoundException {
        CartForReceipt cartForReceipt;
        try {
            cartForReceipt = this.receiptCosmosClient.getCartDocument(cartId);
        } catch (CartNotFoundException e) {
            String errorMsg = String.format("Receipt not found with the biz-event id %s", cartId);
            throw new CartNotFoundException(errorMsg, e);
        }

        if (cartForReceipt == null) {
            String errorMsg = String.format("Receipt retrieved with the biz-event id %s is null", cartId);
            throw new CartNotFoundException(errorMsg);
        }
        return cartForReceipt;
    }
}