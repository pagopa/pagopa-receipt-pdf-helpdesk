package it.gov.pagopa.receipt.pdf.helpdesk.client;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.helpdesk.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.helpdesk.exception.BizEventNotFoundException;

public interface BizEventCosmosClient {

    /**
     * Retrieve biz-even document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return biz-event document
     * @throws BizEventNotFoundException in case no biz-event has been found with the given idEvent
     */
    BizEvent getBizEventDocument(String eventId) throws BizEventNotFoundException;

    /**
     * Retrieve biz-even document with the specified organization fiscal code and iuv from CosmosDB database
     *
     * @param organizationFiscalCode the organization fiscal code
     * @param iuv the iuv
     * @return biz-event document
     * @throws BizEventNotFoundException in case no biz-event has been found with the given idEvent
     */
    BizEvent getBizEventDocumentByOrganizationFiscalCodeAndIUV(String organizationFiscalCode, String iuv) throws BizEventNotFoundException;

    /**
     * Retrieve all biz-event documents related to a specific cart from CosmosDB database
     *
     * @param transactionId     id that identifies the cart
     * @param continuationToken Paged query continuation token
     * @param pageSize          the page size
     * @return a list of biz-event document
     */
    Iterable<FeedResponse<BizEvent>> getAllBizEventDocument(String transactionId, String continuationToken, Integer pageSize);
}
