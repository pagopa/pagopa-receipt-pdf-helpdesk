package it.gov.pagopa.receipt.pdf.helpdesk;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.helpdesk.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BlobStorageClientException;
import it.gov.pagopa.receipt.pdf.helpdesk.model.ProblemJson;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger that retrieves a PDF from Azure Blob Storage
 */
public class GetReceiptPdf {

    private final Logger logger = LoggerFactory.getLogger(GetReceiptPdf.class);

    private final ReceiptBlobClient receiptBlobClient;

    public GetReceiptPdf() {
        this.receiptBlobClient = ReceiptBlobClientImpl.getInstance();
    }

    GetReceiptPdf(ReceiptBlobClient receiptBlobClient) {
        this.receiptBlobClient = receiptBlobClient;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It retrieves the receipt pdf with the specified file name
     * <p>
     *
     * @return response with {@link HttpStatus#OK} and the pdf if found
     */
    @FunctionName("GetReceiptPdf")
    public HttpResponseMessage run(
            @HttpTrigger(name = "GetReceiptTrigger",
                    methods = {HttpMethod.GET},
                    route = "pdf-receipts/{file-name}",
                    authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("file-name") String fileName,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (fileName == null || fileName.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid file name")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        try {
            File pdfFile = this.receiptBlobClient.getAttachmentFromBlobStorage(fileName);
            FileInputStream inputStream = new FileInputStream(pdfFile);
            byte [] result = IOUtils.toByteArray(inputStream);
            Files.deleteIfExists(pdfFile.toPath());
            return request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(result)
                    .build();
        } catch (BlobStorageClientException | IOException e) {
            String responseMsg = String.format("Unable to retrieve the receipt pdf with file name %s", fileName);
            logger.error("[{}] {}", context.getFunctionName(), responseMsg, e);
            return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body(ProblemJson.builder()
                    .title(HttpStatus.NOT_FOUND.name())
                    .detail(responseMsg)
                    .status(HttpStatus.NOT_FOUND.value())
                    .build()).build();
        }
    }
}
