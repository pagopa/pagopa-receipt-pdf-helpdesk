package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.helpdesk.model.MassiveRecoverCartResult;
import it.gov.pagopa.receipt.pdf.helpdesk.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.BizEventToReceiptServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.helpdesk.utils.BizEventToReceiptUtils.massiveRecoverCartByStatus;

/**
 * Azure Functions with Timer trigger.
 */
public class RecoverFailedCartScheduled {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedCartScheduled.class);

    private final boolean isEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("FAILED_CART_AUTORECOVER_ENABLED", "true"));

    private final BizEventToReceiptService bizEventToReceiptService;

    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;

    public RecoverFailedCartScheduled() {
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
    }

    RecoverFailedCartScheduled(BizEventToReceiptService bizEventToReceiptService,
                               CartReceiptsCosmosClient cartReceiptsCosmosClient) {
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
    }

    /**
     * This function will be invoked periodically according to the specified schedule.
     * <p>
     * It recovers all the receipts with the following status:
     * - ({@link it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType#INSERTED})
     * - ({@link it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartStatusType#FAILED})
     * <p>
     * It creates the receipts if not exist and send on queue the event in order to proceed with the receipt generation.
     */
    @FunctionName("CartNotSentRecoverScheduled")
    public void run(
            @TimerTrigger(name = "timerInfoCart", schedule = "%RECOVER_FAILED_CART_CRON%") String timerInfo,
            @CosmosDBOutput(
                    name = "CartReceiptDatastore",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<CartForReceipt>> cartForReceiptDocumentdb,
            final ExecutionContext context
    ) {
        if (isEnabled) {
            logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());
            List<CartForReceipt> cartForReceipts = new ArrayList<>();

            cartForReceipts.addAll(recover(context, CartStatusType.INSERTED));
            cartForReceipts.addAll(recover(context, CartStatusType.FAILED));

            cartForReceiptDocumentdb.setValue(cartForReceipts);
        }
    }

    private List<CartForReceipt> recover(ExecutionContext context, CartStatusType statusType) {
        try {
            MassiveRecoverCartResult recoverResult = massiveRecoverCartByStatus(
                    context, bizEventToReceiptService, cartReceiptsCosmosClient, logger, statusType);
            if (recoverResult.getErrorCounter() > 0) {
                logger.error("[{}] Error recovering {} failed cart for status {}",
                        context.getFunctionName(), recoverResult.getErrorCounter(), statusType);
            }
            return recoverResult.getCartItems();
        } catch (Exception e) {
            logger.error("[{}] Unexpected error during recover of failed cart for status {}",
                    context.getFunctionName(), statusType, e);
            return Collections.emptyList();
        }
    }
}