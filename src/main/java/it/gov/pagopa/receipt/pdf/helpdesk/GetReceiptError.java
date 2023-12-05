package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;

import java.util.Base64;
import java.util.Optional;


/**
 * Azure Functions with Azure Http trigger.
 */
public class GetReceiptError {

    private final ReceiptCosmosClient receiptCosmosClient;

    public GetReceiptError() {
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    public GetReceiptError(ReceiptCosmosClient receiptCosmosClient) {
        this.receiptCosmosClient = receiptCosmosClient;
    }


    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("GetReceiptError")
    public HttpResponseMessage run (
            @HttpTrigger(name = "GetReceiptErrorFunction",
                    methods = {HttpMethod.GET},
                    route = "/errors-toreview/{bizvent-id}",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @BindingName("bizvent-id") String eventId,
            final ExecutionContext context) {

        if (eventId != null && !eventId.isBlank()) {
            try {
                ReceiptError receiptError = receiptCosmosClient.getReceiptError(eventId);
                try {
                    receiptError.setMessagePayload(new String(
                            Base64.getMimeDecoder().decode(receiptError.getMessagePayload()))
                    );
                } catch (IllegalArgumentException ignored) {}
                return request.createResponseBuilder(HttpStatus.OK)
                        .body(receiptError)
                        .build();
            } catch (ReceiptNotFoundException e) {
                return request
                        .createResponseBuilder(HttpStatus.NOT_FOUND)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.NOT_FOUND.name())
                                .detail("No Receipt Error to process on bizEvent with id " + eventId)
                                .status(HttpStatus.NOT_FOUND.value())
                                .build())
                        .build();
            } catch (Exception e) {
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail(e.getMessage())
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
            }
        }

        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(ProblemJson.builder()
                        .title(HttpStatus.BAD_REQUEST.name())
                        .detail("Missing valid search parameter")
                        .status(HttpStatus.BAD_REQUEST.value())
                        .build())
                .build();
    }

}