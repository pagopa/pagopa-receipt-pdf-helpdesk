package it.gov.pagopa.receipt.pdf.helpdesk.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

public class ObjectMapperUtils {

    public static <T> T readModelFromFile(String relativePath, Class<T> clazz) throws IOException {

        try (var inputStream = ObjectMapperUtils.class.getClassLoader().getResourceAsStream(relativePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("File not found: " + relativePath);
            }

            var objectMapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            return objectMapper.readValue(inputStream, clazz);
        }
    }
}
