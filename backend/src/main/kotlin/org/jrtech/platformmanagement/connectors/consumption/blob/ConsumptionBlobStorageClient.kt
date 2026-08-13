package org.jrtech.platformmanagement.connectors.consumption.blob

import java.io.InputStream

/**
 * Abstraction over Azure Blob for the Avro → Parquet pipeline.
 *
 * List/read from the **input** container; write Parquet to the **output** container.
 */
interface ConsumptionBlobStorageClient {
    fun listAvroBlobs(dayPathPrefix: String): List<BlobObjectRef>
    fun openBlob(blobName: String): InputStream
    fun writeOutput(
        blobName: String,
        content: ByteArray,
        metadata: Map<String, String> = emptyMap()
    )

    /** List objects in the **output** container under [pathPrefix]. */
    fun listOutputBlobs(pathPrefix: String): List<BlobObjectRef>

    /** Read an object from the **output** container. */
    fun readOutput(blobName: String): ByteArray
}
