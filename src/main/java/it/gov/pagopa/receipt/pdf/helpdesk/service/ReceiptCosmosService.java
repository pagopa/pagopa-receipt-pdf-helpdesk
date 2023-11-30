package it.gov.pagopa.receipt.pdf.helpdesk.service;

import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
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
}
