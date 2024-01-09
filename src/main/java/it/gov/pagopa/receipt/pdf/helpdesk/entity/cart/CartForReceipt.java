package it.gov.pagopa.receipt.pdf.helpdesk.entity.cart;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.ReasonError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CartForReceipt {
    private String id;
    private Set<String> cartPaymentId;
    private Integer totalNotice;
    private CartStatusType status;
    @JsonProperty("inserted_at")
    private long insertedAt;
    private ReasonError reasonError;
}
