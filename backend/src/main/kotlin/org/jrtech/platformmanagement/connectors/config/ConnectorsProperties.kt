package org.jrtech.platformmanagement.connectors.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Root configuration for `app.connectors.*`.
 *
 * Blob/EH use Managed Identity in production (account URL / namespace + credential chain).
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
 * Layout (under each configured root prefix; empty = container root):
 * ```
 * {prefix}/yyyy/MM/dd/HH_mm_ss.avro
 * ```
 *
 * Auth: [storageAccountUrl] + Managed Identity ([DefaultAzureCredential]), or optional
 * [connectionString] for local only.
 */
data class ConsumptionBlobProperties(
    val enabled: Boolean = false,
    /** Optional; reserved for scheduled runner (not required for on-demand import API). */
    val runnerEnabled: Boolean = false,
    /** e.g. https://myaccount.blob.core.windows.net */
    val storageAccountUrl: String = "",
    val container: String = "",
    /**
     * One or more root prefixes before the date hierarchy (no leading/trailing slash needed).
     *
     * Example YAML:
     * ```yaml
     * blob-prefixes:
     *   - eh-capture
     *   - manual/import
     * ```
     *
     * Env index form: `APP_CONNECTOR_BLOB_PREFIXES_0`, `APP_CONNECTOR_BLOB_PREFIXES_1`, …
     */
    val blobPrefixes: List<String> = emptyList(),
    /**
     * Backward-compatible single prefix (or comma-separated list).
     * Merged with [blobPrefixes] via [resolvedBlobPrefixes].
     * Example: `capture` or `eh-capture,manual/import`.
     */
    val blobPrefix: String = "",
    /** Inclusive max calendar days for startDate..endDate (guard). */
    val maxRangeDays: Int = 31,
    /** Max Avro blobs processed in a single import request. */
    val maxBlobsPerJob: Int = 500,
    val requireSourceRefId: Boolean = true,
    /**
     * Optional connection string (local/dev only). Prefer MI + [storageAccountUrl] in production.
     */
    val connectionString: String = ""
) {
    fun isConfigured(): Boolean =
        container.isNotBlank() &&
            (storageAccountUrl.isNotBlank() || connectionString.isNotBlank())

    /**
     * Normalized, de-duplicated root prefixes to visit during import.
     *
     * - Empty configuration → `[""]` (container root only)
     * - Values from [blobPrefixes] and [blobPrefix] (comma-split) are merged
     * - Leading/trailing slashes stripped; order preserved
     * - Use `""` (or a blank list entry) only when you intentionally want the container root
     *   **in addition** to named prefixes; omit all entries for root-only default
     */
    fun resolvedBlobPrefixes(): List<String> {
        val collected = LinkedHashSet<String>()
        var sawExplicitEmpty = false

        fun accept(raw: String) {
            val normalized = raw.trim().trim('/')
            if (normalized.isEmpty()) {
                // Explicit empty only from list entry or singular blank-after-split parts
                sawExplicitEmpty = true
            } else {
                collected += normalized
            }
        }

        for (entry in blobPrefixes) {
            // Each list item may itself be comma-separated (env convenience)
            entry.split(',').forEach { accept(it) }
        }
        if (blobPrefix.isNotBlank()) {
            blobPrefix.split(',').forEach { accept(it) }
        } else if (blobPrefixes.isEmpty()) {
            // Neither list nor singular configured → container root
            return listOf("")
        }

        return buildList {
            if (collected.isEmpty() || sawExplicitEmpty) {
                // Root-only, or root plus named prefixes when "" was explicit
                if (collected.isEmpty()) {
                    add("")
                } else {
                    add("")
                    addAll(collected)
                }
            } else {
                addAll(collected)
            }
        }
    }
}
