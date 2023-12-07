Feature: All about payment events to recover managed by Azure functions receipt-pdf-helpdesk

  Scenario: getReceipt API return receipt stored on datastore
    Given a receipt with eventId "receipt-helpdesk-int-test-id-1" and status "TO_REVIEW" stored into receipt datastore
    When getReceipt API is called with eventId "receipt-helpdesk-int-test-id-1"
    Then the api response has a 200 Http status
    And the receipt has eventId "receipt-helpdesk-int-test-id-1"

  Scenario: getReceiptByOrganizationFiscalCodeAndIUV API return receipt stored on datastore
     Given a receipt with eventId "receipt-helpdesk-int-test-id-2" and status "TO_REVIEW" stored into receipt datastore
     And a biz event with id "receipt-helpdesk-int-test-id-2" and status "DONE" stored on biz-events datastore
     When getReceiptByOrganizationFiscalCodeAndIUV API is called with organizationFiscalCode "intTestOrgCode" and IUV "intTestIuv"
     Then the api response has a 200 Http status
     And the receipt has eventId "receipt-helpdesk-int-test-id-2"

  Scenario: getReceiptError API return receipt-error stored on datastore
    Given a receipt-error with bizEventId "receipt-helpdesk-int-test-id-3" and status "TO_REVIEW" stored into receipt-error datastore
    When getReceiptError API is called with bizEventId "receipt-helpdesk-int-test-id-3"
    Then the api response has a 200 Http status
    And the receipt-error has bizEventId "receipt-helpdesk-int-test-id-3"
    And the receipt-error payload has bizEvent decrypted with eventId "receipt-generator-int-test-id-4"

  Scenario: getReceiptPdf API return receipt pdf store on blob storage
    Given a receipt pdf with filename "int-test-helpdesk-receipt.pdf" stored into blob storage
    When getReceiptPdf API is called with filename "int-test-helpdesk-receipt.pdf"
    Then the api response has a 200 Http status

  Scenario: receiptToReviewed API retrieve a receipt error and updates its status to REVIEWED
    Given a receipt-error with bizEventId "receipt-helpdesk-int-test-id-5" and status "TO_REVIEW" stored into receipt-error datastore
    When receiptToReviewed API is called with bizEventId "receipt-helpdesk-int-test-id-5"
    Then the api response has a 200 Http status
    And the receipt-error with bizEventId "receipt-helpdesk-int-test-id-5" is recovered from datastore
    And the receipt-error has not status "TO_REVIEW"

  Scenario: recoverFailedReceipt API retrieve a receipt in status FAILED and updates its status to INSERTED
    Given a receipt with eventId "receipt-helpdesk-int-test-id-6" and status "FAILED" stored into receipt datastore
    And a biz event with id "receipt-helpdesk-int-test-id-6" and status "DONE" stored on biz-events datastore
    When recoverFailedReceipt API is called with eventId "receipt-helpdesk-int-test-id-6"
    Then the api response has a 200 Http status
    And the receipt with eventId "receipt-helpdesk-int-test-id-6" is recovered from datastore
    And the receipt has not status "FAILED"

  Scenario: recoverFailedReceiptMassive API retrieve all the receipts in status FAILED and updates their status to INSERTED
    Given a list of 10 receipts in status "FAILED" stored into receipt datastore starting from eventId "receipt-helpdesk-int-test-id-7"
    And a list of 10 biz events in status "DONE" stored into biz-events datastore starting from eventId "receipt-helpdesk-int-test-id-7"
    When recoverFailedReceiptMassive API is called with status "FAILED" as query param
    Then the api response has a 200 Http status
    And the list of receipt is recovered from datastore and no receipt in the list has status "FAILED"