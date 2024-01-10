const { CosmosClient } = require("@azure/cosmos");
const { createReceipt, createReceiptError, createReceiptMessage, createCart } = require("./common");

const cosmos_db_conn_string = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const receiptErrorContainerId = process.env.RECEIPT_ERROR_COSMOS_DB_CONTAINER_NAME;
const receiptMessageContainerId = process.env.RECEIPT_MESSAGE_COSMOS_DB_CONTAINER_NAME;
const cartContainerId = process.env.CART_COSMOS_DB_CONTAINER_NAME;

const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);
const receiptErrorContainer = client.database(databaseId).container(receiptErrorContainerId);
const receiptMessageContainer = client.database(databaseId).container(receiptMessageContainerId);
const cartContainer = client.database(databaseId).container(cartContainerId);

//RECEIPT
async function createDocumentInReceiptsDatastore(id, status) {
    let receipt = createReceipt(id, status);
    try {
        return await receiptContainer.items.create(receipt);
    } catch (err) {
        console.log(err);
    }
}

async function getDocumentFromReceiptsDatastoreByEventId(id) {
    return await receiptContainer.items
        .query({
            query: "SELECT * from c WHERE c.eventId=@eventId",
            parameters: [{ name: "@eventId", value: id }]
        })
        .fetchNext();
}

async function deleteMultipleDocumentsFromReceiptsDatastoreByEventId(eventId) {
    let documents = await getDocumentFromReceiptsDatastoreByEventId(eventId);

    documents?.resources?.forEach(el => {
        deleteDocumentFromReceiptsDatastore(el.id);
    })
}

async function deleteDocumentFromReceiptsDatastore(id) {
    try {
        return await receiptContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

//RECEIPT-ERROR
async function createDocumentInReceiptErrorDatastore(id, status) {
    let receipt = createReceiptError(id, status);
    try {
        return await receiptErrorContainer.items.create(receipt);
    } catch (err) {
        console.log(err);
    }
}

//RECEIPT-MESSAGE
async function createDocumentInReceiptIoMessageDatastore(eventId, messageId) {
    let message = createReceiptMessage(eventId, messageId);
    try {
        return await receiptMessageContainer.items.create(message);
    } catch (err) {
        console.log(err);
    }
}

async function getDocumentFromReceiptsErrorDatastoreByBizEventId(id) {
    return await receiptErrorContainer.items
        .query({
            query: "SELECT * from c WHERE c.bizEventId=@bizEventId",
            parameters: [{ name: "@bizEventId", value: id }]
        })
        .fetchNext();
}

async function deleteMultipleDocumentFromReceiptErrorDatastoreByEventId(id) {
    let documents = await getDocumentFromReceiptsErrorDatastoreByBizEventId(id);

    documents?.resources?.forEach(el => {
        deleteDocumentFromReceiptErrorDatastore(el.id);
    })
}

async function deleteDocumentFromReceiptErrorDatastore(id) {
    try {
        return await receiptErrorContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function deleteDocumentFromReceiptMessageDatastore(id) {
    try {
        return await receiptMessageContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function updateReceiptToFailed(id) {

    const operations =
        [
            { op: 'replace', path: '/status', value: 'FAILED' }
        ];

    try {
        return await receiptContainer.item(id, id).patch(operations);
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

// CART
async function createDocumentInCartDatastore(id, bizEventIds, status) {
    let cart = createCart(id, bizEventIds, status);
    try {
        return await cartContainer.items.create(cart);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromCartDatastore(id) {
    try {
        return await cartContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function getDocumentFromCartDatastoreById(id) {
    return await cartContainer.items
        .query({
            query: "SELECT * from c WHERE c.id=@cartId",
            parameters: [{ name: "@cartId", value: id }]
        })
        .fetchNext();
}
async function deleteAllTestReceipts() {
    let response = await receiptContainer.items.query({
        query: 'SELECT * from c WHERE c.id LIKE @id',
        parameters: [{ name: "@id", value: "%receipt-helpdesk-int-test%" }]
    }).fetchNext();

    let response2 = await receiptContainer.items
        .query({
            query: "SELECT * from c WHERE c.eventId=@eventId",
            parameters: [{ name: "@eventId", value: "%receipt-helpdesk-int-test%" }]
        })
        .fetchNext();


    let receiptList = response.resources.concat(response2.resources);
    if (receiptList.length > 0) {
        receiptList.forEach((receipt) => {
            console.log("\n Deleting receipt with id " + id);
            deleteDocumentFromReceiptsDatastore(receipt.id);
        });
    }
}

async function deleteAllTestReceiptsError() {
    let response = await receiptErrorContainer.items.query({
        query: 'SELECT * from c WHERE c.bizEventId LIKE @bizEventId',
        parameters: [{ name: "@bizEventId", value: "%receipt-helpdesk-int-test%" }]
    }).fetchNext();

    let receiptErrorList = response.resources;
    if (receiptErrorList.length > 0) {
        receiptErrorList.forEach((receiptError) => {
            console.log("\n Deleting receiptError with id " + id);
            deleteDocumentFromReceiptErrorDatastore(receiptError.id);
        })
    }
}

async function deleteAllTestReceiptsMessage() {
    let response = await receiptErrorContainer.items.query({
        query: 'SELECT * from c WHERE c.messageId LIKE @messageId',
        parameters: [{ name: "@messageId", value: "%receipt-helpdesk-int-test-message%" }]
    }).fetchNext();

    let receiptErrorList = response.resources;
    if (receiptErrorList.length > 0) {
        receiptErrorList.forEach((receiptMessage) => {
            console.log("\n Deleting receiptMessage with id " + receiptMessage.messageId);
            deleteDocumentFromReceiptMessageDatastore(receiptMessage.id);
        })
    }
}

module.exports = {
    createDocumentInReceiptsDatastore,
    getDocumentFromReceiptsDatastoreByEventId,
    deleteMultipleDocumentsFromReceiptsDatastoreByEventId,
    deleteDocumentFromReceiptsDatastore,
    updateReceiptToFailed,
    deleteAllTestReceipts,
    deleteAllTestReceiptsMessage,
    createDocumentInReceiptIoMessageDatastore,

    deleteDocumentFromReceiptErrorDatastore,
    deleteDocumentFromReceiptMessageDatastore,
    deleteMultipleDocumentFromReceiptErrorDatastoreByEventId,
    createDocumentInReceiptErrorDatastore,
    getDocumentFromReceiptsErrorDatastoreByBizEventId,
    deleteAllTestReceiptsError,

    createDocumentInCartDatastore,
    deleteDocumentFromCartDatastore,
    getDocumentFromCartDatastoreById
}