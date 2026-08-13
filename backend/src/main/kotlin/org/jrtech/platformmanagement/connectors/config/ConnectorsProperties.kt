package org.jrtech.platformmanagement.connectors.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Root configuration for `app.connectors.*`.
 *
 * Blob/EH use [org.jrtech.platformmanagement.config.azure.AzureCredentialFactory]
 * (shared `app.azure.credential`: UAMI → service principal → SAMI).
 */
@ConfigurationProperties(prefix = "app.connectors")
data class ConnectorsProperties(
    val consumptionEventHub: ConsumptionEventHubProperties = ConsumptionEventHubProperties(),
    val consumptionBlob: ConsumptionBlobProperties = ConsumptionBlobProperties()
)

data class ConsumptionEventHubProperties(
    /** Master switch: when false, start API rejects and auto-start is ignored. */
    val enabled: Boolean = false,
    /**
     * When true and [enabled], start the processor on application ready.
     * Default false — operators control lifecycle via Maintainer Web API.
     */
    val autoStart: Boolean = false,
    /** Fully qualified namespace, e.g. `myns.servicebus.windows.net`. */
    val fullyQualifiedNamespace: String = "",
    val eventHubName: String = "",
    val consumerGroup: String = "\$Default",
    /** Checkpoint Blob account URL, e.g. `https://acct.blob.core.windows.net`. */
    val checkpointStorageAccountUrl: String = "",
    val checkpointContainer: String = "eh-checkpoints",
    /** When true, reject events without source_ref_id (recommended for dual-path). */
    val requireSourceRefId: Boolean = true,
    /** After this many consecutive permanent failures on a partition event, skip (poison). */
    val poisonSkipAfter: Int = 5
) {
    fun isConfigured(): Boolean =
        fullyQualifiedNamespace.isNotBlank() &&
            eventHubName.isNotBlank() &&
            checkpointStorageAccountUrl.isNotBlank() &&
            checkpointContainer.isNotBlank()
}

/**
 * Blob hierarchical storage for consumption Avro files.
 *
 * Input layout (under each [inputBlobPrefixes] / [inputBlobPrefix]; empty = input container root):
 * ```
 * {input-blob-prefix}/yyyy/MM/dd/HH_mm_ss.avro
 * ```
 *
 * Output layout in the output container (under [outputBlobPrefix]):
 * ```
 * {output-blob-prefix}/yyyy/MM/dd/HH_mm_ss.parquet
 * ```
 *
 * Auth: [storageAccountName] + shared Azure credential (UAMI / SP / SAMI),
 * or optional [connectionString] for local only.
 */
data class ConsumptionBlobProperties(
    val enabled: Boolean = false,
    /** Optional; reserved for scheduled runner (not required for on-demand import API). */
    val runnerEnabled: Boolean = false,
    /**
     * Globally unique storage account name (not a URL).
     * Public Azure endpoint is `https://{name}.blob.core.windows.net`.
     */
    val storageAccountName: String = "",
    /** Input container holding Avro files. */
    val inputContainer: String = "",
    /** Output container for processed Parquet files. */
    val outputContainer: String = "",
    /**
     * Keep only Avro records whose `object_type` / `objectType` equals this value.
     */
    val objectType: String = "consumption_metric",
    /** Max Avro files processed in parallel (one pipeline thread per file). */
    val maxConcurrentPipelines: Int = 4,
    /**
     * One or more root folders in the **input** container before `yyyy/MM/dd/`.
     *
     * ```yaml
     * input-blob-prefixes:
     *   - eh-capture
     *   - manual/import
     * ```
     *
     * Env: `APP_CONNECTOR_BLOB_INPUT_PREFIXES_0`, `_1`, …
     */
    val inputBlobPrefixes: List<String> = emptyList(),
    /**
     * Singular or comma-separated input prefixes. Merged with [inputBlobPrefixes].
     * Env: `APP_CONNECTOR_BLOB_INPUT_PREFIX`.
     */
    val inputBlobPrefix: String = "",
    /**
     * Single root folder in the **output** container before `yyyy/MM/dd/`.
     * Empty = output container root. Env: `APP_CONNECTOR_BLOB_OUTPUT_PREFIX`.
     */
    val outputBlobPrefix: String = "",
    /** Inclusive max calendar days for startDate..endDate (guard). */
    val maxRangeDays: Int = 31,
    /** Max Avro blobs processed in a single import request. */
    val maxBlobsPerJob: Int = 500,
    /** Exclusive claim lease for one Avro file across JVMs. */
    val claimLeaseSeconds: Int = 900,
    val requireSourceRefId: Boolean = true,
    /**
     * Optional connection string (local/dev only). Prefer MI + [storageAccountName] in production.
     */
    val connectionString: String = ""
) {
    fun isConfigured(): Boolean =
        inputContainer.isNotBlank() &&
            outputContainer.isNotBlank() &&
            (storageAccountName.isNotBlank() || connectionString.isNotBlank())

    fun resolvedObjectType(): String = objectType.trim().ifBlank { "consumption_metric" }

    fun resolvedMaxConcurrentPipelines(): Int = maxConcurrentPipelines.coerceAtLeast(1)

    fun resolvedClaimLease(): java.time.Duration =
        java.time.Duration.ofSeconds(claimLeaseSeconds.toLong().coerceAtLeast(30))

    /**
     * Normalized input roots to list. Empty configuration → `[""]` (container root).
     * [inputBlobPrefixes] and [inputBlobPrefix] (comma-split) are merged; slashes stripped.
     */
    fun resolvedInputBlobPrefixes(): List<String> {
        val collected = LinkedHashSet<String>()
        var sawExplicitEmpty = false

        fun accept(raw: String) {
            val normalized = raw.trim().trim('/')
            if (normalized.isEmpty()) {
                sawExplicitEmpty = true
            } else {
                collected += normalized
            }
        }

        for (entry in inputBlobPrefixes) {
            entry.split(',').forEach { accept(it) }
        }
        if (inputBlobPrefix.isNotBlank()) {
            inputBlobPrefix.split(',').forEach { accept(it) }
        } else if (inputBlobPrefixes.isEmpty()) {
            return listOf("")
        }

        return buildList {
            if (collected.isEmpty() || sawExplicitEmpty) {
                add("")
                addAll(collected)
            } else {
                addAll(collected)
            }
        }
    }

    fun resolvedOutputBlobPrefix(): String = outputBlobPrefix.trim().trim('/')

    /**
     * Blob service endpoint derived from [storageAccountName].
     * Empty when the name is blank (connection-string auth does not need it).
     */
    fun blobEndpointUrl(): String {
        val name = storageAccountName.trim().lowercase()
        if (name.isEmpty()) return ""
        return "https://$name.blob.core.windows.net"
    }
}
