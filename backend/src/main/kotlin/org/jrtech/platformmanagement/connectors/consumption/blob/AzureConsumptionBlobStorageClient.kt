package org.jrtech.platformmanagement.connectors.consumption.blob

import com.azure.core.credential.TokenCredential
import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.BlobListDetails
import com.azure.storage.blob.models.ListBlobsOptions
import org.jrtech.platformmanagement.connectors.config.ConsumptionBlobProperties
import org.jrtech.platformmanagement.logging.logger
import java.io.InputStream
import java.time.Duration

/**
 * Azure Blob client for hierarchical consumption Avro paths.
 *
 * Uses shared [TokenCredential] (UAMI / service principal / SAMI) when
 * [ConsumptionBlobProperties.connectionString] is empty; otherwise connection string (local only).
 */
class AzureConsumptionBlobStorageClient(
    private val properties: ConsumptionBlobProperties,
    private val tokenCredential: TokenCredential? = null
) : ConsumptionBlobStorageClient {

    private val log = logger()
    private val serviceClient: BlobServiceClient = buildServiceClient()
    private val inputContainerClient: BlobContainerClient =
        serviceClient.getBlobContainerClient(requireContainer(properties.inputContainer, "input-container"))
    private val outputContainerClient: BlobContainerClient =
        serviceClient.getBlobContainerClient(requireContainer(properties.outputContainer, "output-container"))

    override fun listAvroBlobs(dayPathPrefix: String): List<BlobObjectRef> {
        val prefix = dayPathPrefix.trim().let { p ->
            when {
                p.isEmpty() -> ""
                p.endsWith("/") -> p
                else -> "$p/"
            }
        }
        val options = ListBlobsOptions()
            .setPrefix(prefix)
            .setDetails(BlobListDetails().setRetrieveMetadata(false))
        val out = mutableListOf<BlobObjectRef>()
        inputContainerClient.listBlobs(options, Duration.ofMinutes(2)).forEach { item ->
            val name = item.name ?: return@forEach
            if (isConsumptionAvroBlob(name)) {
                val props = item.properties
                out += BlobObjectRef(
                    name = name,
                    size = props?.contentLength,
                    etag = props?.eTag?.toString()?.trim('"').orEmpty()
                )
            }
        }
        log.debug("Listed {} avro blob(s) under prefix '{}'", out.size, prefix)
        return out.sortedBy { it.name }
    }

    override fun openBlob(blobName: String): InputStream {
        val client = inputContainerClient.getBlobClient(blobName)
        if (!client.exists()) {
            throw IllegalArgumentException("Blob not found: $blobName")
        }
        return client.openInputStream()
    }

    override fun writeOutput(
        blobName: String,
        content: ByteArray,
        metadata: Map<String, String>
    ) {
        val client = outputContainerClient.getBlobClient(blobName)
        client.upload(BinaryData.fromBytes(content), true)
        if (metadata.isNotEmpty()) {
            client.setMetadata(metadata)
        }
        log.debug("Wrote output blob {} ({} bytes)", blobName, content.size)
    }

    override fun listOutputBlobs(pathPrefix: String): List<BlobObjectRef> {
        val prefix = pathPrefix.trim().let { p ->
            when {
                p.isEmpty() -> ""
                p.endsWith("/") -> p
                else -> "$p/"
            }
        }
        val options = ListBlobsOptions()
            .setPrefix(prefix)
            .setDetails(BlobListDetails().setRetrieveMetadata(false))
        val out = mutableListOf<BlobObjectRef>()
        outputContainerClient.listBlobs(options, Duration.ofMinutes(2)).forEach { item ->
            val name = item.name ?: return@forEach
            val props = item.properties
            out += BlobObjectRef(
                name = name,
                size = props?.contentLength,
                etag = props?.eTag?.toString()?.trim('"').orEmpty()
            )
        }
        return out.sortedBy { it.name }
    }

    override fun readOutput(blobName: String): ByteArray {
        val client = outputContainerClient.getBlobClient(blobName)
        if (!client.exists()) {
            throw IllegalArgumentException("Output blob not found: $blobName")
        }
        return client.downloadContent().toBytes()
    }

    private fun buildServiceClient(): BlobServiceClient {
        val builder = BlobServiceClientBuilder()
        val conn = properties.connectionString.trim()
        if (conn.isNotEmpty()) {
            log.info(
                "Consumption blob client: connection-string auth (input={}, output={})",
                properties.inputContainer,
                properties.outputContainer
            )
            builder.connectionString(conn)
        } else {
            val url = properties.blobEndpointUrl()
            require(url.isNotEmpty()) {
                "app.connectors.consumption-blob.storage-account-name is required when connection-string is empty"
            }
            val credential = requireNotNull(tokenCredential) {
                "TokenCredential is required when connection-string is empty " +
                    "(configure app.azure.credential.client-id / client-secret)"
            }
            log.info(
                "Consumption blob client: TokenCredential auth (url={}, input={}, output={})",
                url,
                properties.inputContainer,
                properties.outputContainer
            )
            builder.endpoint(url).credential(credential)
        }
        return builder.buildClient()
    }

    private fun requireContainer(value: String, property: String): String {
        val name = value.trim()
        require(name.isNotEmpty()) { "app.connectors.consumption-blob.$property is required" }
        return name
    }

    companion object {
        /** Matches terminal names like `14_30_00.avro` (any path prefix). */
        private val FILE_NAME_PATTERN = Regex("""^\d{2}_\d{2}_\d{2}\.avro$""", RegexOption.IGNORE_CASE)

        fun isConsumptionAvroBlob(blobName: String): Boolean {
            val fileName = blobName.substringAfterLast('/').trim()
            return FILE_NAME_PATTERN.matches(fileName)
        }
    }
}
