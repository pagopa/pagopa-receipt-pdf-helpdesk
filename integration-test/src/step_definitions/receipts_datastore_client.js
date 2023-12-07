const { CosmosClient } = require("@azure/cosmos");
const { createReceipt, createReceiptError } = require("./common");

const cosmos_db_conn_string = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const receiptErrorContainerId = process.env.RECEIPT_ERROR_COSMOS_DB_CONTAINER_NAME;

const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);
const receiptErrorContainer = client.database(databaseId).container(receiptErrorContainerId);

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

module.exports = {
    createDocumentInReceiptsDatastore,
    getDocumentFromReceiptsDatastoreByEventId,
    deleteMultipleDocumentsFromReceiptsDatastoreByEventId,
    deleteDocumentFromReceiptsDatastore,
    updateReceiptToFailed,

    deleteDocumentFromReceiptErrorDatastore,
    deleteMultipleDocumentFromReceiptErrorDatastoreByEventId,
    createDocumentInReceiptErrorDatastore,
    getDocumentFromReceiptsErrorDatastoreByBizEventId
}