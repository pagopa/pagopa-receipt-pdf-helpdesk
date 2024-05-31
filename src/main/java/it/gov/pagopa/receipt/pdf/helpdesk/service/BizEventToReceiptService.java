package it.gov.pagopa.receipt.pdf.helpdesk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;

import java.util.List;

public interface BizEventToReceiptService {

    /**
     * Handles sending biz-events as message to queue and updates receipt's status
     *
     * @param bizEventList Biz-event list from CosmosDB
     * @param receipt  Receipt to update
     */
    void handleSendMessageToQueue(List<BizEvent> bizEventList, Receipt receipt);

    /**
     * Retrieve conditionally the transaction creation date from biz-event
     *
     * @param bizEvent Biz-event from CosmosDB
     * @return transaction date
     */
    String getTransactionCreationDate(BizEvent bizEvent);

    /**
     * Calls PDVTokenizerService to tokenize the fiscal codes for both Debtor & Payer (if present)
     *
     * @param bizEvent BizEvent where fiscalCodes are stored
     * @param receipt Receipt to update in case of errors
     * @param eventData Event data to update with tokenized fiscalCodes
     * @throws JsonProcessingException if an error occur when parsing input or output
     * @throws PDVTokenizerException if an error occur when invoking the PDV Tokenizer
     */
    void tokenizeFiscalCodes(BizEvent bizEvent, Receipt receipt, EventData eventData)  throws JsonProcessingException, PDVTokenizerException;

    void handleSaveReceipt(Receipt receipt, ReceiptStatusType regenerationStatus);

    /**
     * Retrieve all events that are associated to the cart with the specified id
     *
     * @param cartId the id of the cart
     * @return a list of biz-events
     */
    List<BizEvent> getCartBizEvents(String cartId);

    /**
     * Creates the receipt for a cart, using the tokenizer service to mask the PII, based on
     * the provided list of BizEvent
     *
     * @param bizEventList a list og BizEvent
     * @return a receipt
     */
    Receipt createCartReceipt(List<BizEvent> bizEventList);
}
