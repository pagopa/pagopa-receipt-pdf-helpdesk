package it.gov.pagopa.receipt.pdf.helpdesk.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.receipt.pdf.helpdesk.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.ReceiptNotFoundException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class GenerateReceiptUtils {

    private static final String PATTERN_FORMAT = "yyyy.MM.dd.HH.mm.ss";

    private static final String WORKING_DIRECTORY_PATH = System.getenv().getOrDefault("WORKING_DIRECTORY_PATH", "");

    public static Path createWorkingDirectory() throws IOException {
        File workingDirectory = new File(WORKING_DIRECTORY_PATH);
        if (!workingDirectory.exists()) {
            try {
                Files.createDirectory(workingDirectory.toPath());
            } catch (FileAlreadyExistsException ignored) {
                // The working directory already exist we don't need to create it
            }
        }
        return Files.createTempDirectory(workingDirectory.toPath(),
                DateTimeFormatter.ofPattern(PATTERN_FORMAT)
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.now()));
    }

    public static void deleteTempFolder(Path workingDirPath, Logger logger) {
        try {
            FileUtils.deleteDirectory(workingDirPath.toFile());
        } catch (IOException e) {
            logger.warn("Unable to clear working directory: {}", workingDirPath, e);
        }
    }

    public static Receipt getReceipt(ExecutionContext context, BizEvent bizEvent,
                               ReceiptCosmosClient receiptCosmosClient, Logger logger) throws ReceiptNotFoundException {
        Receipt receipt;
        //Retrieve receipt from CosmosDB
        try {
            receipt = receiptCosmosClient.getReceiptDocument(bizEvent.getId());
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("[%s] Receipt not found with the biz-event id %s",
                    context.getFunctionName(), bizEvent.getId());
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = "[{}] Receipt retrieved with the biz-event id {} is null";
            logger.error(errorMsg, context.getFunctionName(), bizEvent.getId());
            throw new ReceiptNotFoundException(errorMsg);
        }
        return receipt;
    }


}
