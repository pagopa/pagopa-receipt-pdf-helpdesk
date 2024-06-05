package it.gov.pagopa.receipt.pdf.helpdesk.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

public class ObjectMapperUtils {

    private static final ModelMapper modelMapper;
    private static final ObjectMapper objectMapper;

    /**
     * Model mapper property setting are specified in the following block.
     * Default property matching strategy is set to Strict see {@link MatchingStrategies}
     * Custom mappings are added using {@link ModelMapper#addMappings(PropertyMap)}
     */
    static {
        modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        objectMapper = new ObjectMapper();
    }

    /**
     * Hide from public usage.
     */
    private ObjectMapperUtils() {
    }

    /**
     * Encodes an object to a string
     *
     * @param value Object to be encoded
     * @return encoded string
     */
    public static String writeValueAsString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Maps string to object of defined Class
     *
     * @param string   String to map
     * @param outClass Class to be mapped to
     * @param <T>      Defined Class
     * @return object of the defined Class
     */
    public static <T> T mapString(final String string, Class<T> outClass) throws JsonProcessingException {
        return objectMapper.readValue(string, outClass);
    }
    
    /**
     * Maps {@code source} to {@code destination}.
     *
     * @param source      object to map from
     * @param destination object to map to
     */
    public static <S, D> D map(final S source, D destination) {
        modelMapper.map(source, destination);
        return destination;
    }


}
