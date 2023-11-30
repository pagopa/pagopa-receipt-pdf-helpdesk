package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
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
            receipt = receiptCosmosClient.getReceiptDocument(eventId);
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
}