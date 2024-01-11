package it.gov.pagopa.receipt.pdf.helpdesk.utils;

import com.azure.cosmos.models.FeedResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.Transfer;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.MassiveRecoverCartResult;
import it.gov.pagopa.receipt.pdf.helpdesk.model.MassiveRecoverResult;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BizEventToReceiptUtils {

    private static final String REMITTANCE_INFORMATION_REGEX = "/TXT/(.*)";

    private static final List<String> listOrigin;

    static {
        listOrigin = Arrays.asList(System.getenv().getOrDefault("LIST_VALID_ORIGINS", "IO").split(","));
    }

    public static Receipt getEvent(
            String eventId,
            ExecutionContext context,
            BizEventToReceiptService bizEventToReceiptService,
            BizEventCosmosClient bizEventCosmosClient,
            ReceiptCosmosService receiptCosmosService,
            Receipt receipt,
            Logger logger,
            Boolean isCart
    ) throws BizEventNotFoundException, PDVTokenizerException, JsonProcessingException {

        List<BizEvent> listCart = null;
        BizEvent bizEvent;

        if (isCart) {
            listCart = bizEventToReceiptService.getCartBizEvents(eventId);
            bizEvent = listCart.get(0);
        } else {
            bizEvent = bizEventCosmosClient.getBizEventDocument(eventId);
        }

        if (isCart) {
            Integer intTotalNotice = Integer.parseInt(bizEvent.getPaymentInfo().getTotalNotice());
            if (!intTotalNotice.equals(listCart.size())) {
                return null;
            }
            for (BizEvent event : listCart) {
                if (isBizEventInvalid(event, context, logger)) {
                    return null;
                }
            }
        } else if (isBizEventInvalid(bizEvent, context, logger)) {
            return null;
        }


        if (receipt == null) {
            try {
                receipt = receiptCosmosService.getReceipt(eventId);
            } catch (ReceiptNotFoundException e) {
                receipt = BizEventToReceiptUtils.createReceipt(bizEvent,
                        bizEventToReceiptService, logger);
                EventData eventData = receipt.getEventData();
                if (isCart) {
                    AtomicReference<BigDecimal> amount = new AtomicReference<>(BigDecimal.ZERO);
                    List<CartItem> cartItems = new ArrayList<>();
                    listCart.forEach(event -> {
                        BigDecimal amountExtracted = getAmount(bizEvent);
                        amount.updateAndGet(v -> v.add(amountExtracted));
                        cartItems.add(
                                CartItem.builder()
                                        .payeeName(bizEvent.getCreditor() != null ?
                                                bizEvent.getCreditor().getCompanyName() : null)
                                        .subject(getItemSubject(bizEvent))
                                        .build());
                    });

                    if (!amount.get().equals(BigDecimal.ZERO)) {
                        eventData.setAmount(amount.get().toString());
                    }

                    eventData.setCart(cartItems);
                }
                receipt.setStatus(ReceiptStatusType.FAILED);
            }
        }

        if (receipt != null && (
                receipt.getStatus().equals(ReceiptStatusType.FAILED) ||
                        receipt.getStatus().equals(ReceiptStatusType.INSERTED) ||
                        receipt.getStatus().equals(ReceiptStatusType.NOT_QUEUE_SENT)
        )) {
            if (receipt.getEventData() == null || receipt.getEventData().getDebtorFiscalCode() == null) {
                tokenizeReceipt(bizEventToReceiptService, isCart ? listCart : Collections.singletonList(bizEvent), receipt);
            }
            receipt.setStatus(ReceiptStatusType.INSERTED);
            bizEventToReceiptService.handleSendMessageToQueue(isCart ? listCart :
                    Collections.singletonList(bizEvent), receipt);
            if (receipt.getStatus() != ReceiptStatusType.NOT_QUEUE_SENT) {
                receipt.setInserted_at(System.currentTimeMillis());
                receipt.setReasonErr(null);
                receipt.setReasonErrPayer(null);
            }
            return receipt;
        }
        return null;
    }

    public static MassiveRecoverResult massiveRecoverByStatus(
            ExecutionContext context,
            BizEventToReceiptService bizEventToReceiptService,
            BizEventCosmosClient bizEventCosmosClient,
            ReceiptCosmosService receiptCosmosService,
            Logger logger,
            ReceiptStatusType statusType) {
        int errorCounter = 0;
        List<Receipt> receiptList = new ArrayList<>();
        String continuationToken = null;
        do {
            Iterable<FeedResponse<Receipt>> feedResponseIterator =
                    receiptCosmosService.getFailedReceiptByStatus(continuationToken, 100, statusType);

            for (FeedResponse<Receipt> page : feedResponseIterator) {
                for (Receipt receipt : page.getResults()) {
                    try {
                        Receipt restored = getEvent(receipt.getEventId(), context, bizEventToReceiptService,
                                bizEventCosmosClient, receiptCosmosService, receipt, logger, receipt.getIsCart() != null ?
                                receipt.getIsCart() : false);
                        receiptList.add(restored);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                        errorCounter++;
                    }
                }
                continuationToken = page.getContinuationToken();
            }
        } while (continuationToken != null);
        return MassiveRecoverResult.builder()
                .receiptList(receiptList)
                .errorCounter(errorCounter)
                .build();
    }

    /**
     * Creates a new instance of Receipt, using the tokenizer service to mask the PII, based on
     * the provided BizEvent
     *
     * @param bizEvent instance of BizEvent
     * @param service  implementation of the BizEventToReceipt service to use
     * @return generated instance of Receipt
     */
    public static Receipt createReceipt(BizEvent bizEvent, BizEventToReceiptService service, Logger logger) {
        Receipt receipt = new Receipt();

        // Insert biz-event data into receipt
        receipt.setId(bizEvent.getId() + UUID.randomUUID());
        receipt.setEventId(bizEvent.getId());

        EventData eventData = new EventData();

        try {
            service.tokenizeFiscalCodes(bizEvent, receipt, eventData);
        } catch (Exception e) {
            logger.error("Error tokenizing receipt with bizEventId {}", bizEvent.getId(), e);
            receipt.setStatus(ReceiptStatusType.FAILED);
            return receipt;
        }

        eventData.setTransactionCreationDate(
                service.getTransactionCreationDate(bizEvent));
        BigDecimal amount = getAmount(bizEvent);
        eventData.setAmount(!amount.equals(BigDecimal.ZERO) ? amount.toString() : null);

        CartItem item = new CartItem();
        item.setPayeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null);
        item.setSubject(getItemSubject(bizEvent));
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        receipt.setEventData(eventData);
        return receipt;
    }

    public static List<CartItem> getCartItems(BizEvent bizEvent) {
        CartItem item = new CartItem();
        item.setPayeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null);
        item.setSubject(getItemSubject(bizEvent));
        List<CartItem> cartItems = Collections.singletonList(item);
        return cartItems;
    }

    /**
     * Checks if the instance of Biz Event is in status DONE and contains all the required information to process
     * in the receipt generation
     *
     * @param bizEvent BizEvent to validate
     * @param context  Function context
     * @param logger   Function logger
     * @return boolean to determine if the proposed event is invalid
     */
    public static boolean isBizEventInvalid(BizEvent bizEvent, ExecutionContext context, Logger logger) {

        if (bizEvent == null) {
            logger.debug("[{}] event is null", context.getFunctionName());
            return true;
        }

        if (!bizEvent.getEventStatus().equals(BizEventStatusType.DONE)) {
            logger.debug("[{}] event with id {} discarded because in status {}",
                    context.getFunctionName(), bizEvent.getId(), bizEvent.getEventStatus());
            return true;
        }

        if (bizEvent.getDebtor().getEntityUniqueIdentifierValue() == null ||
                (bizEvent.getDebtor().getEntityUniqueIdentifierValue().equals("ANONIMO") &&
                        (bizEvent.getPayer() == null || bizEvent.getPayer().getEntityUniqueIdentifierValue() == null))) {
            logger.debug("[{}] event with id {} discarded because debtor identifier is missing or ANONIMO",
                    context.getFunctionName(), bizEvent.getId());
            return true;
        }

        if (bizEvent.getPaymentInfo() != null) {
            String totalNotice = bizEvent.getPaymentInfo().getTotalNotice();

            if (totalNotice != null) {
                int intTotalNotice;

                try {
                    intTotalNotice = Integer.parseInt(totalNotice);

                } catch (NumberFormatException e) {
                    logger.error("[{}] event with id {} discarded because has an invalid total notice value: {}",
                            context.getFunctionName(), bizEvent.getId(),
                            totalNotice,
                            e);
                    return true;
                }

                if (intTotalNotice > 1) {
                    logger.debug("[{}] event with id {} discarded because is part of a payment cart ({} total notice)",
                            context.getFunctionName(), bizEvent.getId(),
                            intTotalNotice);
                    return true;
                }
            }
        }

        return false;
    }

    public static void tokenizeReceipt(BizEventToReceiptService service, List<BizEvent> bizEvents, Receipt receipt)
            throws PDVTokenizerException, JsonProcessingException {
        BizEvent firstEvent = bizEvents.get(0);
        if (receipt.getEventData() == null) {
            EventData eventData = new EventData();
            receipt.setEventData(eventData);
            eventData.setTransactionCreationDate(
                    service.getTransactionCreationDate(firstEvent));

            AtomicReference<BigDecimal> amount = new AtomicReference<>(BigDecimal.ZERO);
            List<CartItem> cartItems = new ArrayList<>();
            bizEvents.forEach(bizEvent -> {
                BigDecimal amountExtracted = getAmount(bizEvent);
                amount.updateAndGet(v -> v.add(amountExtracted));
                cartItems.add(
                        CartItem.builder()
                                .payeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null)
                                .subject(getItemSubject(bizEvent))
                                .build());
            });

            if (!amount.get().equals(BigDecimal.ZERO)) {
                eventData.setAmount(amount.get().toString());
            }

            eventData.setCart(cartItems);

        }
        service.tokenizeFiscalCodes(firstEvent, receipt, receipt.getEventData());
    }


    /**
     * Retrieve RemittanceInformation from BizEvent
     *
     * @param bizEvent BizEvent from which retrieve the data
     * @return the remittance information
     */
    public static String getItemSubject(BizEvent bizEvent) {
        if (bizEvent.getPaymentInfo() != null && bizEvent.getPaymentInfo().getRemittanceInformation() != null) {
            return bizEvent.getPaymentInfo().getRemittanceInformation();
        }
        List<Transfer> transferList = bizEvent.getTransferList();
        if (transferList != null && !transferList.isEmpty()) {
            double amount = 0;
            String remittanceInformation = null;
            for (Transfer transfer : transferList) {
                double transferAmount;
                try {
                    transferAmount = Double.parseDouble(transfer.getAmount());
                } catch (Exception ignored) {
                    continue;
                }
                if (amount < transferAmount) {
                    amount = transferAmount;
                    remittanceInformation = transfer.getRemittanceInformation();
                }
            }
            return formatRemittanceInformation(remittanceInformation);
        }
        return null;
    }

    private static String formatRemittanceInformation(String remittanceInformation) {
        if (remittanceInformation != null) {
            Pattern pattern = Pattern.compile(REMITTANCE_INFORMATION_REGEX);
            Matcher matcher = pattern.matcher(remittanceInformation);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return remittanceInformation;
    }

    public static boolean isReceiptStatusValid(Receipt receipt) {
        return receipt.getStatus() != ReceiptStatusType.FAILED && receipt.getStatus() != ReceiptStatusType.NOT_QUEUE_SENT;
    }

    public static MassiveRecoverCartResult massiveRecoverCartByStatus(
            ExecutionContext context, BizEventToReceiptService bizEventToReceiptService,
            CartReceiptsCosmosClient cartReceiptsCosmosClient,
            Logger logger, CartStatusType statusType) {
        int errorCounter = 0;
        List<CartForReceipt> cartItems = new ArrayList<>();
        String continuationToken = null;
        if (statusType == null) {
            throw new IllegalArgumentException("at least one status must be specified");
        }
        do {
            Iterable<FeedResponse<CartForReceipt>> feedResponseIterator = null;

            if (statusType.equals(CartStatusType.FAILED)) {
                feedResponseIterator = cartReceiptsCosmosClient.getFailedCarts(continuationToken, 100);
            }
            if (statusType.equals(CartStatusType.INSERTED)) {
                feedResponseIterator = cartReceiptsCosmosClient.getInsertedCarts(continuationToken, 100);
            }

            assert feedResponseIterator != null;
            for (FeedResponse<CartForReceipt> page : feedResponseIterator) {
                for (CartForReceipt cart : page.getResults()) {
                    try {
                        List<BizEvent> bizEventList = bizEventToReceiptService.getCartBizEvents(cart.getId());
                        Receipt receipt = bizEventToReceiptService.createCartReceipt(bizEventList);

                        if (!isReceiptStatusValid(receipt)) {
                            logger.error("[{}] Failed to process cart with id {}: fail to tokenize fiscal codes",
                                    context.getFunctionName(), cart.getId());
                            throw new Exception("receipt status not valid");
                        }

                        // Add receipt to items to be saved on CosmosDB
                        bizEventToReceiptService.handleSaveReceipt(receipt);

                        if (!isReceiptStatusValid(receipt)) {
                            throw new Exception("receipt not valid");
                        }

                        // Send biz event as message to queue (to be processed from the other function)
                        bizEventToReceiptService.handleSendMessageToQueue(bizEventList, receipt);
                        cart.setStatus(CartStatusType.SENT);
                        cartItems.add(cart);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                        errorCounter++;
                    }
                }
                continuationToken = page.getContinuationToken();
            }
        } while (continuationToken != null);
        return MassiveRecoverCartResult.builder()
                .cartItems(cartItems)
                .errorCounter(errorCounter)
                .build();
    }

    public static BigDecimal getAmount(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null
                && bizEvent.getTransactionDetails().getTransaction().getGrandTotal() != 0) {
            return formatAmount(bizEvent.getTransactionDetails().getTransaction().getGrandTotal());
        }
        if (bizEvent.getPaymentInfo() != null && bizEvent.getPaymentInfo().getAmount() != null) {
            return new BigDecimal(bizEvent.getPaymentInfo().getAmount());
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal formatAmount(long grandTotal) {
        BigDecimal amount = new BigDecimal(grandTotal);
        BigDecimal divider = new BigDecimal(100);
        return amount.divide(divider, 2, RoundingMode.UNNECESSARY);
    }

    public static boolean isFromAuthenticatedOrigin(BizEvent bizEvent) {
        return bizEvent.getTransactionDetails() != null &&
                ((bizEvent.getTransactionDetails().getTransaction() != null &&
                        bizEvent.getTransactionDetails().getTransaction().getOrigin() != null &&
                        listOrigin.contains(bizEvent.getTransactionDetails().getTransaction().getOrigin())) ||
                        (bizEvent.getTransactionDetails().getInfo() != null &&
                                bizEvent.getTransactionDetails().getInfo().getClientId() != null &&
                                listOrigin.contains(bizEvent.getTransactionDetails().getInfo().getClientId())
                        ));
    }

    private BizEventToReceiptUtils() {}
}
