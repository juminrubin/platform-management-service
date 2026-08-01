package org.jrtech.platformmanagement.dto

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate

/**
 * Parameters for on-demand consumption Avro retrieve/import from hierarchical blob storage.
 * Bound from `GET /api/v1/connectors/{id}` query parameters when id is consumption-storage.
 */
data class ConsumptionBlobImportRequest(
    /** Inclusive start of the calendar day range (UTC day folders yyyy/MM/dd). */
    @field:NotNull
    val startDate: LocalDate,

    /** Inclusive end of the calendar day range. */
    @field:NotNull
    val endDate: LocalDate,

    /**
     * When true, Avro files are downloaded and parsed but not written to the database.
     */
    val dryRun: Boolean = false,

    /**
     * Optional subset of configured root prefixes to visit.
     * When null or empty, all configured [app.connectors.consumption-blob] prefixes are used.
     * Values must match a resolved configured prefix (or `""` for container root).
     */
    val blobPrefixes: List<String>? = null
)

data class ConsumptionBlobImportResponse(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dryRun: Boolean,
    val requestedBy: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    /** Root prefixes that were visited (empty string = container root). */
    val blobPrefixes: List<String> = emptyList(),
    val daysVisited: Int,
    val blobsDiscovered: Int,
    val blobsProcessed: Int,
    val blobsFailed: Int,
    val rowsParsed: Int,
    val rowsInserted: Int,
    val rowsDuplicate: Int,
    val rowsInvalid: Int,
    val rowsFailed: Int,
    val errors: List<String> = emptyList()
)

data class ConsumptionBlobConnectorStatusResponse(
    val id: String,
    val enabled: Boolean,
    val configured: Boolean,
    val storageAccountUrl: String?,
    val container: String?,
    /**
     * Resolved root prefixes (empty string entry means container root).
     * Prefer this over the legacy [blobPrefix] field.
     */
    val blobPrefixes: List<String> = emptyList(),
    /**
     * Legacy single-prefix view: first resolved prefix, or null when only container root.
     */
    @Deprecated("Use blobPrefixes")
    val blobPrefix: String? = null,
    val maxRangeDays: Int,
    val maxBlobsPerJob: Int,
    val requireSourceRefId: Boolean,
    val detail: String?
)
