package it.gov.pagopa.receipt.pdf.helpdesk.client;

import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;

public interface BizEventCosmosClient {
    BizEvent getBizEventDocument(String eventId) throws ReceiptNotFoundException, BizEventNotFoundException;
}
