package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.helpdesk.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.helpdesk.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class GetReceiptByOrganizationFiscalCodeAndIUV {

    private final Logger logger = LoggerFactory.getLogger(GetReceiptByOrganizationFiscalCodeAndIUV.class);

    private final ReceiptCosmosService receiptCosmosService;
    private final BizEventCosmosClient bizEventCosmosClient;

    public GetReceiptByOrganizationFiscalCodeAndIUV() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    GetReceiptByOrganizationFiscalCodeAndIUV(ReceiptCosmosService receiptCosmosService, BizEventCosmosClient bizEventCosmosClient) {
        this.receiptCosmosService = receiptCosmosService;
        this.bizEventCosmosClient = bizEventCosmosClient;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It retrieves the receipt with the specified organization fiscal code and iuv
     * <p>
     *
     * @return response with {@link HttpStatus#OK} and the receipt if found
     */
    @FunctionName("GetReceiptByOrganizationFiscalCodeAndIUV")
    public HttpResponseMessage run(
            @HttpTrigger(name = "GetReceiptByOrganizationFiscalCodeAndIUVTrigger",
                    methods = {HttpMethod.GET},
                    route = "receipts/organizations/{organization-fiscal-code}/iuvs/{iuv}",
                    authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("organization-fiscal-code") String organizationFiscalCode,
            @BindingName("iuv") String iuv,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (organizationFiscalCode == null
                || organizationFiscalCode.isBlank()
                || iuv == null
                || iuv.isBlank()
        ) {
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid organization fiscal code and iuv")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        BizEvent bizEvent;
        try {
            bizEvent = this.bizEventCosmosClient
                    .getBizEventDocumentByOrganizationFiscalCodeAndIUV(organizationFiscalCode, iuv);
        } catch (BizEventNotFoundException e) {
            String responseMsg = String.format("Unable to retrieve the biz-event with organization fiscal code %s and iuv %s",
                    organizationFiscalCode, iuv);
            logger.error("[{}] {}", context.getFunctionName(), responseMsg, e);
            return request.createResponseBuilder(HttpStatus.NOT_FOUND).body(responseMsg).build();
        }

        try {
            Receipt receipt = this.receiptCosmosService.getReceipt(bizEvent.getId());
            return request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(receipt)
                    .build();
        } catch (ReceiptNotFoundException e) {
            String responseMsg = String.format("Unable to retrieve the receipt with eventId %s", bizEvent.getId());
            logger.error("[{}] {}", context.getFunctionName(), responseMsg, e);
            return request.createResponseBuilder(HttpStatus.NOT_FOUND).body(responseMsg).build();
        }
    }
}
