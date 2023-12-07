const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
let fs = require('fs');
const { sleep } = require("./common");
const { createDocumentInBizEventsDatastore, deleteDocumentFromBizEventsDatastore } = require("./biz_events_datastore_client");
const {
    getDocumentByIdFromReceiptsDatastoreByEventId,
    deleteDocumentFromReceiptsDatastore,
    createDocumentInReceiptsDatastore,
    deleteMultipleDocumentsFromReceiptsDatastoreByEventId,
    deleteMultipleDocumentFromReceiptErrorDatastoreByEventId,
    createDocumentInReceiptErrorDatastore,
    deleteDocumentFromReceiptErrorDatastore
} = require("./receipts_datastore_client");
const {
    getReceipt,
    getReceiptByOrganizationFiscalCodeAndIUV,
    getReceiptError,
    getReceiptPdf
} = require("./api_helpdesk_client");
const { uploadBlobFromLocalPath, deleteBlob } = require("./blob_storage_client");

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);

// initialize variables
let eventId = null;
let responseAPI = null;
let responseCosmos = null;
let receipt = null;
let receiptError = null;
let event = null;
let receiptPdfFileName = null;

// After each Scenario
After(async function () {
    // remove event
    if (eventId != null) {
        await deleteDocumentFromBizEventsDatastore(eventId);
        await deleteMultipleDocumentsFromReceiptsDatastoreByEventId(eventId);
        await deleteMultipleDocumentFromReceiptErrorDatastoreByEventId(eventId);
    }
    if(receiptPdfFileName != null){
        await deleteBlob(receiptPdfFileName);
        fs.unlinkSync(receiptPdfFileName);
    }
});

//Given
Given('a biz event with id {string} stored on biz-events datastore with status {string}', async function (id, status) {
    eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromBizEventsDatastore(eventId);

    let bizEventStoreResponse = await createDocumentInBizEventsDatastore(eventId, status);
    assert.strictEqual(bizEventStoreResponse.statusCode, 201);
});

Given('a receipt with eventId {string} stored into receipt datastore', async function (id) {
    eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptsDatastore(id);

    let receiptsStoreResponse = await createDocumentInReceiptsDatastore(id);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
});

Given('a receipt-error with bizEventId {string} stored into receipt-error datastore', async function (id) {
    eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptErrorDatastore(id);

    let receiptsStoreResponse = await createDocumentInReceiptErrorDatastore(id);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
});

Given("a receipt pdf with filename {string} stored into blob storage", async function (fileName){
    receiptPdfFileName = fileName;
     // prior cancellation to avoid dirty cases
    await deleteBlob(fileName);

    fs.writeFileSync(fileName, "", "binary");
    let blobStorageResponse = await uploadBlobFromLocalPath(fileName, fileName);
    assert.notStrictEqual(blobStorageResponse.status, 500);
});

//When
When("getReceipt API is called with eventId {string}", async function (id) {
    responseAPI = await getReceipt(id);
    receipt = responseAPI.data;
});

When("getReceiptByOrganizationFiscalCodeAndIUV API is called with organizationFiscalCode {string} and IUV {string}", async function (orgCode, iuv) {
    responseAPI = await getReceiptByOrganizationFiscalCodeAndIUV(orgCode, iuv);
    receipt = responseAPI.data;
});

When("getReceiptError API is called with bizEventId {string}", async function (id) {
    responseAPI = await getReceiptError(id);
    receiptError = responseAPI.data;
});

When("getReceiptPdf API is called with filename {string}", async function (filename) {
    responseAPI = await getReceiptPdf(filename);
});

//Then
Then('the api response has a {int} Http status', function (expectedStatus) {
    assert.strictEqual(responseAPI.status, expectedStatus);
});

Then('the receipt has eventId {string}', function (targetId) {
    assert.strictEqual(receipt.eventId, targetId);
});

Then('the receipt has not the status {string}', function (targetStatus) {
    assert.notStrictEqual(receipt.status, targetStatus);
});

Then('the receipt has not the status {string} after {int} ms', async function (targetStatus, time) {
    await sleep(time);
    responseCosmos = await getDocumentByIdFromReceiptsDatastoreByEventId(eventId);
    assert.notStrictEqual(responseCosmos.resources[0].status, targetStatus);
});

Then('the receipts datastore returns the receipt', async function () {
    assert.notStrictEqual(responseCosmos.resources.length, 0);
    eventId = responseCosmos.resources[0].eventId;
    assert.strictEqual(responseCosmos.resources.length, 1);
});

Then("the receipt-error has bizEventId {string}", async function (id) {
    assert.strictEqual(receiptError.bizEventId, id);
});

Then("the receipt-error payload has bizEvent decrypted with eventId {string}", async function (id) {
    assert.strictEqual(receiptError.messagePayload.eventId, id);
});




