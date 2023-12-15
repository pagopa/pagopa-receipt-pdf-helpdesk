package it.gov.pagopa.receipt.pdf.helpdesk.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.helpdesk.utils.ObjectMapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils.getItemSubject;

public class BizEventToReceiptServiceImpl implements BizEventToReceiptService {

    private final Logger logger = LoggerFactory.getLogger(BizEventToReceiptServiceImpl.class);

    private final PDVTokenizerServiceRetryWrapper pdvTokenizerService;
    private final ReceiptQueueClient queueClient;

    private final BizEventCosmosClient bizEventCosmosClient;

    private final ReceiptCosmosClient receiptCosmosClient;

    public BizEventToReceiptServiceImpl() {
        this.pdvTokenizerService = new PDVTokenizerServiceRetryWrapperImpl();
        this.queueClient = ReceiptQueueClientImpl.getInstance();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    public BizEventToReceiptServiceImpl(PDVTokenizerServiceRetryWrapper pdvTokenizerService,
                                        ReceiptQueueClient queueClient, BizEventCosmosClient bizEventCosmosClient,
                                        ReceiptCosmosClient receiptCosmosClient) {
        this.pdvTokenizerService = pdvTokenizerService;
        this.queueClient = queueClient;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosClient = receiptCosmosClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSendMessageToQueue(List<BizEvent> bizEventList, Receipt receipt) {
        //Encode biz-event to base64 string
        String messageText = Base64.getMimeEncoder().encodeToString(
                Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEventList)).getBytes(StandardCharsets.UTF_8));

        //Add message to the queue
        int statusCode;
        try {
            Response<SendMessageResult> sendMessageResult = queueClient.sendMessageToQueue(messageText);
            statusCode = sendMessageResult.getStatusCode();
        } catch (Exception e) {
            statusCode = ReasonErrorCode.ERROR_QUEUE.getCode();
            if (bizEventList.size() == 1) {
                logger.error("Sending BizEvent with id {} to queue failed", bizEventList.get(0).getId(), e);
            } else {
                logger.error("Failed to enqueue cart with id {}",
                        bizEventList.get(0).getTransactionDetails().getTransaction().getIdTransaction(), e);
            }
        }

        if (statusCode != HttpStatus.CREATED.value()) {
            String errorString = String.format(
                    "[BizEventToReceiptService] Error sending message to queue for receipt with eventId %s",
                    receipt.getEventId());
            handleError(receipt, ReceiptStatusType.NOT_QUEUE_SENT, errorString, statusCode);
            //Error info
            logger.error(errorString);
        }
    }

    /**
     * Handles errors for queue and cosmos and updates receipt's status accordingly
     *
     * @param receipt Receipt to update
     */
    private void handleError(Receipt receipt, ReceiptStatusType statusType, String errorMessage, int errorCode) {
        receipt.setStatus(statusType);
        ReasonError reasonError = new ReasonError(errorCode, errorMessage);
        receipt.setReasonErr(reasonError);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTransactionCreationDate(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            return bizEvent.getTransactionDetails().getTransaction().getCreationDate();

        } else if (bizEvent.getPaymentInfo() != null) {
            return bizEvent.getPaymentInfo().getPaymentDateTime();
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tokenizeFiscalCodes(BizEvent bizEvent, Receipt receipt, EventData eventData) throws JsonProcessingException, PDVTokenizerException {
        try {
            if (bizEvent.getDebtor() != null && bizEvent.getDebtor().getEntityUniqueIdentifierValue() != null) {
                eventData.setDebtorFiscalCode("ANONIMO".equals(bizEvent.getDebtor().getEntityUniqueIdentifierValue()) ?
                        bizEvent.getDebtor().getEntityUniqueIdentifierValue() :
                        pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getDebtor().getEntityUniqueIdentifierValue())
                );
            }
            if (bizEvent.getPayer() != null && bizEvent.getPayer().getEntityUniqueIdentifierValue() != null) {
                eventData.setPayerFiscalCode(
                        pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getPayer().getEntityUniqueIdentifierValue())
                );
            } else if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getUser() != null &&
                    bizEvent.getTransactionDetails().getUser().getFiscalCode() != null) {
                eventData.setPayerFiscalCode(
                        pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getTransactionDetails().getUser().getFiscalCode())
                );
            }
        } catch (PDVTokenizerException e) {
            handleTokenizerException(receipt, e.getMessage(), e.getStatusCode());
            throw e;
        } catch (JsonProcessingException e) {
            handleTokenizerException(receipt, e.getMessage(), ReasonErrorCode.ERROR_PDV_MAPPING.getCode());
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSaveReceipt(Receipt receipt) {
        int statusCode;

        try {
            receipt.setStatus(ReceiptStatusType.INSERTED);
            receipt.setInsertedAt(System.currentTimeMillis());
            CosmosItemResponse<Receipt> response = receiptCosmosClient.saveReceipts(receipt);

            statusCode = response.getStatusCode();
        } catch (Exception e) {
            statusCode = ReasonErrorCode.ERROR_COSMOS.getCode();
            logger.error("Save receipt with eventId {} on cosmos failed", receipt.getEventId(), e);
        }

        if (statusCode != (HttpStatus.CREATED.value())) {
            String errorString = String.format(
                    "[BizEventToReceiptService] Error saving receipt to cosmos for receipt with eventId %s, cosmos client responded with status %s",
                    receipt.getEventId(), statusCode);
            handleError(receipt, ReceiptStatusType.FAILED, errorString, statusCode);
            //Error info
            logger.error(errorString);
        }
    }

    /**
     * Handles errors for PDV tokenizer and updates receipt's status accordingly
     *
     * @param receipt      Receipt to update
     * @param errorMessage Message to save
     * @param statusCode   StatusCode to save
     */
    private void handleTokenizerException(Receipt receipt, String errorMessage, int statusCode) {
        receipt.setStatus(ReceiptStatusType.FAILED);
        ReasonError reasonError = new ReasonError(statusCode, errorMessage);
        receipt.setReasonErr(reasonError);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BizEvent> getCartBizEvents(long cartId) {
        List<BizEvent> bizEventList = new ArrayList<>();
        String continuationToken = null;
        do {
            Iterable<FeedResponse<BizEvent>> feedResponseIterator =
                    this.bizEventCosmosClient.getAllBizEventDocument(cartId, continuationToken, 100);

            for (FeedResponse<BizEvent> page : feedResponseIterator) {
                bizEventList.addAll(page.getResults());
                continuationToken = page.getContinuationToken();
            }
        } while (continuationToken != null);
        return bizEventList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt createCartReceipt(List<BizEvent> bizEventList) {
        // TODO: capire come gestire la creazione della receipt
        Receipt receipt = new Receipt();
        // TODO il campo è transactionDetails.transaction.transactionId non idTransaction
        long carId = bizEventList.get(0).getTransactionDetails().getTransaction().getIdTransaction();

        // Insert biz-event data into receipt
        receipt.setId(String.format("%s-%s", carId, UUID.randomUUID()));
        receipt.setEventId(Long.toString(carId));
        receipt.setIsCart(true);

        EventData eventData = new EventData();
        try {
            tokenizeFiscalCodes(bizEventList.get(0), receipt, eventData);
        } catch (Exception e) {
            logger.error("Error tokenizing receipt for cart with id {}",
                    carId, e);
            receipt.setStatus(ReceiptStatusType.FAILED);
            return receipt;
        }

        eventData.setTransactionCreationDate(
                getTransactionCreationDate(bizEventList.get(0)));
        eventData.setAmount(bizEventList.get(0).getPaymentInfo() != null ?
                bizEventList.get(0).getPaymentInfo().getAmount() : null);


        List<CartItem> cartItems = new ArrayList<>();
        bizEventList.forEach(bizEvent -> cartItems.add(
                CartItem.builder()
                        .payeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null)
                        .subject(getItemSubject(bizEvent))
                        .build()));
        eventData.setCart(cartItems);

        receipt.setEventData(eventData);
        return receipt;
    }

}
