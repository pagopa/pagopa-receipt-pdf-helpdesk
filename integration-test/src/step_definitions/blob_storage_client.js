const { BlobServiceClient, StorageSharedKeyCredential } = require('@azure/storage-blob');

const blobStorageContainerName = process.env.BLOB_STORAGE_CONTAINER_NAME;

// Azure Storage resource name
const accountName = process.env.BLOB_STORAGE_ACCOUNT_NAME;
if (!accountName) throw Error("Azure Storage accountName not found");

// Azure Storage resource key
const accountKey = process.env.BLOB_STORAGE_ACCOUNT_KEY;
if (!accountKey) throw Error("Azure Storage accountKey not found");

// Create credential
const sharedKeyCredential = new StorageSharedKeyCredential(
    accountName,
    accountKey
);

const blobServiceClient = new BlobServiceClient(`https://${accountName}.blob.core.windows.net`, sharedKeyCredential);
const containerClient = blobServiceClient.getContainerClient(blobStorageContainerName);

async function uploadBlobFromLocalPath(fileName, localFilePath) {
    const blobClient = containerClient.getBlockBlobClient(fileName);

    try {
        return await blobClient.uploadFile(localFilePath);
    } catch (err) {
        return { status: 500 }
    }
}

async function deleteBlob(blobName) {
    // include: Delete the base blob and all of its snapshots.
    // only: Delete only the blob's snapshots and not the blob itself.
    const options = {
        deleteSnapshots: 'include' // or 'only'
    }

    // Create blob client from container client
    const blockBlobClient = containerClient.getBlockBlobClient(blobName);

    await blockBlobClient.deleteIfExists(options);
}

module.exports = {
    uploadBlobFromLocalPath,
    deleteBlob
}