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
     * When true, Avro files are downloaded and parsed but Parquet is not written.
     */
    val dryRun: Boolean = false,

    /**
     * Optional subset of configured input prefixes.
     * When null or empty, all configured [app.connectors.consumption-blob] input prefixes are used.
     */
    val inputBlobPrefixes: List<String>? = null,

    /** When true, steal existing SUCCEEDED claims and convert again. */
    val force: Boolean = false
)

data class ConsumptionBlobImportResponse(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dryRun: Boolean,
    val requestedBy: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    /** Input roots visited (empty string = container root). */
    val inputBlobPrefixes: List<String> = emptyList(),
    /** Single output root (empty = output container root). */
    val outputBlobPrefix: String = "",
    val daysVisited: Int,
    val blobsDiscovered: Int,
    val blobsProcessed: Int,
    val blobsFailed: Int,
    val blobsSkipped: Int = 0,
    val recordsRead: Int,
    val recordsMatched: Int,
    val recordsWritten: Int,
    val recordsInvalid: Int,
    val outputFiles: Int,
    val errors: List<String> = emptyList()
)

/**
 * Data-plane view of Avro blobs under hierarchical day folders for [fromDate]..[untilDate].
 * Returned by `GET /api/v1/consumption/blob`.
 */
data class ConsumptionBlobViewResponse(
    val fromDate: LocalDate,
    val untilDate: LocalDate,
    /** Input roots visited (empty string = container root). */
    val inputBlobPrefixes: List<String> = emptyList(),
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
    val storageAccountName: String?,
    val inputContainer: String?,
    val outputContainer: String?,
    val objectType: String,
    val inputBlobPrefixes: List<String> = emptyList(),
    val outputBlobPrefix: String = "",
    val maxRangeDays: Int,
    val maxBlobsPerJob: Int,
    val requireSourceRefId: Boolean,
    val detail: String?
)
