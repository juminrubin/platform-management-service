package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.config.ConsumptionBlobProperties
import org.jrtech.platformmanagement.logging.logger
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.BlobListDetails
import com.azure.storage.blob.models.ListBlobsOptions
import java.io.InputStream
import java.time.Duration

/**
 * Azure Blob client for hierarchical consumption Avro paths.
 * Uses Managed Identity ([DefaultAzureCredentialBuilder]) when [ConsumptionBlobProperties.connectionString]
 * is empty; otherwise connection string (local only).
 */
class AzureConsumptionBlobStorageClient(
    private val properties: ConsumptionBlobProperties
) : ConsumptionBlobStorageClient {

    private val log = logger()
    private val containerClient: BlobContainerClient = buildContainerClient()

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
        containerClient.listBlobs(options, Duration.ofMinutes(2)).forEach { item ->
            val name = item.name ?: return@forEach
            if (isConsumptionAvroBlob(name)) {
                out += BlobObjectRef(name = name, size = item.properties?.contentLength)
            }
        }
        log.debug("Listed {} avro blob(s) under prefix '{}'", out.size, prefix)
        return out.sortedBy { it.name }
    }

    override fun openBlob(blobName: String): InputStream {
        val client = containerClient.getBlobClient(blobName)
        if (!client.exists()) {
            throw IllegalArgumentException("Blob not found: $blobName")
        }
        return client.openInputStream()
    }

    private fun buildContainerClient(): BlobContainerClient {
        val container = properties.container.trim()
        require(container.isNotEmpty()) { "app.connectors.consumption-blob.container is required" }

        val builder = BlobServiceClientBuilder()
        val conn = properties.connectionString.trim()
        if (conn.isNotEmpty()) {
            log.info("Consumption blob client: connection-string auth (container={})", container)
            builder.connectionString(conn)
        } else {
            val url = properties.storageAccountUrl.trim().removeSuffix("/")
            require(url.isNotEmpty()) {
                "app.connectors.consumption-blob.storage-account-url is required when connection-string is empty"
            }
            log.info("Consumption blob client: Managed Identity / DefaultAzureCredential (url={})", url)
            builder.endpoint(url).credential(DefaultAzureCredentialBuilder().build())
        }
        return builder.buildClient().getBlobContainerClient(container)
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
