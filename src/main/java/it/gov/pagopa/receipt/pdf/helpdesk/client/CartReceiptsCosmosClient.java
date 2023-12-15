package it.gov.pagopa.receipt.pdf.helpdesk.client;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.CartNotFoundException;

public interface CartReceiptsCosmosClient {

    CartForReceipt getCartItem(String eventId) throws CartNotFoundException;

    CosmosItemResponse<CartForReceipt> saveCart(CartForReceipt receipt);
}
