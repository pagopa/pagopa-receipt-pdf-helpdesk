package it.gov.pagopa.receipt.pdf.helpdesk.model.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class Cart {

    private List<Item> items;
    private String amountPartial;

}
