package org.jrtech.platformmanagement.dto

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate

/**
 * Parameters for a consumption Avro import job (runtime job config / start override).
 * Configured via `PUT /api/v1/connectors/consumption-storage/config` and applied on start.
 * Data view: `GET /api/v1/consumption/blob?fromDate=&untilDate=` (defaults: today UTC).
 * Domain rows: `/api/v1/consumptions`.
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

/**
 * Data-plane view of Avro blobs under hierarchical day folders for [fromDate]..[untilDate].
 * Returned by `GET /api/v1/consumption/blob`.
 */
data class ConsumptionBlobViewResponse(
    val fromDate: LocalDate,
    val untilDate: LocalDate,
    /** Root prefixes visited (empty string = container root). */
    val blobPrefixes: List<String> = emptyList(),
    val daysVisited: Int,
    val blobCount: Int,
    val blobs: List<ConsumptionBlobObjectView> = emptyList(),
    val errors: List<String> = emptyList(),
    /**
     * Last import job summary when its configured range overlaps [fromDate]..[untilDate].
     */
    val lastImport: ConsumptionBlobImportResponse? = null
)

data class ConsumptionBlobObjectView(
    /** Full path within the container, e.g. `eh-capture/2024/07/01/14_30_00.avro`. */
    val name: String,
    val size: Long? = null
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
