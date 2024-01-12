Feature: All about payment events to recover managed by Azure functions receipt-pdf-helpdesk

  Scenario: getReceiptMessage API return receipt-error stored on datastore
    Given a receipt-io-message with bizEventId "receipt-helpdesk-int-test-id-3" and messageId "receipt-helpdesk-int-test-message-id-3" stored into receipt-io-message datastore
    When getReceiptMessage API is called with messageId "receipt-helpdesk-int-test-message-id-3"
    Then the api response has a 200 Http status
    And the receipt-message has messageId "receipt-helpdesk-int-test-message-id-3"
    And the receipt-message has eventId "receipt-helpdesk-int-test-id-3"