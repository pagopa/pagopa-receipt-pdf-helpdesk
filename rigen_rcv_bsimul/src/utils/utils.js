const { CosmosClient } = require("@azure/cosmos");
const { get } = require("http");

// receipt
const cosmos_db_conn_string = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);
// biz
const biz_cosmos_db_conn_string = process.env.BIZ_COSMOS_CONN_STRING || "";
const biz_databaseId = process.env.BIZ_COSMOS_DB_NAME;
const biz_ContainerId = process.env.BIZ_COSMOS_DB_CONTAINER_NAME;
const biz_client = new CosmosClient(biz_cosmos_db_conn_string);
const biz_Container = biz_client.database(biz_databaseId).container(biz_ContainerId);



async function getReceiptsByCf(cf) {
    return await receiptContainer.items
        .query({
            query: `SELECT * FROM c WHERE
            c.eventData.debtorFiscalCode = (@fiscalcode)
            or c.eventData.payerFiscalCode = (@fiscalcode)`,
            parameters: [
                { name: "@fiscalcode", value: cf },

            ]
        })
        .fetchAll();
}


async function updateBizStatusToRETRY(bizId) {

    const operations =
        [
            { op: 'replace', path: '/eventStatus', value: 'RETRY' },
            { op: 'remove', path: '/transactionDetails' }
        ];

    try {
        return await biz_Container.item(bizId, bizId).patch(operations);
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function getBiz(bizId) {
    return await biz_Container.items
        .query({
            query: `SELECT * FROM c WHERE
            c.id = (@bizId)`,
            parameters: [
                { name: "@bizId", value: bizId },

            ]
        })
        .fetchAll();
}



module.exports = {
    getReceiptsByCf, updateBizStatusToRETRY, getBiz
}


