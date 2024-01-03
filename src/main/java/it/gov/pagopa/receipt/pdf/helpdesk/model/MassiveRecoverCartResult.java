package it.gov.pagopa.receipt.pdf.helpdesk.model;

import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.receipt.Receipt;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MassiveRecoverCartResult {

    private List<CartForReceipt> cartItems;
    private int errorCounter;
}
