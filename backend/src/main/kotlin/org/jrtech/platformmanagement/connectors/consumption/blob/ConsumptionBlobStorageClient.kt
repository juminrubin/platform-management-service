package org.jrtech.platformmanagement.connectors.consumption.blob

import java.io.InputStream

/**
 * Abstraction over Azure Blob (hierarchical paths) for consumption Avro import.
 */
interface ConsumptionBlobStorageClient {
    fun listAvroBlobs(dayPathPrefix: String): List<BlobObjectRef>
    fun openBlob(blobName: String): InputStream
}
