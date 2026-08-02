package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.runtime.ConnectorConfigSupport
import org.jrtech.platformmanagement.connectors.runtime.ConnectorLogBuffer
import org.jrtech.platformmanagement.connectors.runtime.ManagedConnector
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConnectorInfoResponse
import org.jrtech.platformmanagement.dto.ConsumptionBlobConnectorStatusResponse
import org.jrtech.platformmanagement.dto.ConsumptionBlobImportRequest
import org.jrtech.platformmanagement.dto.ConsumptionBlobImportResponse
import org.jrtech.platformmanagement.dto.ConsumptionBlobObjectView
import org.jrtech.platformmanagement.dto.ConsumptionBlobViewResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.service.ConsumptionService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Backend process for hierarchical consumption Avro import from Azure Blob.
 *
 * Control plane: [ManagedConnector] — configure date range / prefixes, start/stop job.
 * Data plane:
 * - blob view by date range: `GET /api/v1/consumption/blob?fromDate=&untilDate=`
 * - domain consumption rows: `GET /api/v1/consumptions`
 *
 * [start] runs one import job using the runtime job configuration.
 * [stop] requests cooperative cancel between blobs (in-flight blob finishes).
 */
@Service
class ConsumptionBlobContainerConnector(
    private val connectorsProperties: ConnectorsProperties,
    private val storageClientProvider: ObjectProvider<ConsumptionBlobStorageClient>,
    private val avroFileReader: ConsumptionAvroFileReader,
    private val consumptionService: ConsumptionService
) : ManagedConnector {

    private val log = logger()
    private val processLog = ConnectorLogBuffer()
    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    private val lastStartedBy = AtomicReference<String?>(null)
    private val lastStartedAt = AtomicReference<java.time.Instant?>(null)
    private val lastStoppedBy = AtomicReference<String?>(null)
    private val lastStoppedAt = AtomicReference<java.time.Instant?>(null)
    private val lastError = AtomicReference<String?>(null)
    private val lastImportResult = AtomicReference<ConsumptionBlobImportResponse?>(null)

    // Runtime job configuration (what to load) — set via configure / start.
    private val jobStartDate = AtomicReference<LocalDate?>(null)
    private val jobEndDate = AtomicReference<LocalDate?>(null)
    private val jobDryRun = AtomicBoolean(false)
    private val jobBlobPrefixes = AtomicReference<List<String>?>(null)

    override val id: ConnectorId = ConnectorId.CONSUMPTION_BLOB_AVRO

    override fun isEnabled(): Boolean = connectorsProperties.consumptionBlob.enabled

    override fun info(): ConnectorInfoResponse {
        val p = connectorsProperties.consumptionBlob
        val isRunning = running.get()
        val statusLabel = when {
            !p.enabled -> "DISABLED"
            !p.isConfigured() -> "DOWN"
            isRunning -> "RUNNING"
            lastError.get() != null -> "DEGRADED"
            else -> "STOPPED"
        }
        val detail = when {
            !p.enabled -> "disabled"
            !p.isConfigured() -> "incomplete-config (need container + storage-account-url or connection-string)"
            isRunning -> "import-in-progress"
            lastError.get() != null -> "last-error"
            lastImportResult.get() != null -> "idle-last-import-available"
            else -> "ready"
        }
        return ConnectorInfoResponse(
            id = id.pathId,
            enabled = p.enabled,
            configured = p.isConfigured(),
            running = isRunning,
            status = statusLabel,
            detail = detail,
            lastStartedBy = lastStartedBy.get(),
            lastStartedAt = lastStartedAt.get(),
            lastStoppedBy = lastStoppedBy.get(),
            lastStoppedAt = lastStoppedAt.get(),
            lastError = lastError.get(),
            attributes = buildMap {
                put("cancelRequested", cancelRequested.get().toString())
                lastImportResult.get()?.let { r ->
                    put("lastImportFinishedAt", r.finishedAt.toString())
                    put("lastImportBlobsProcessed", r.blobsProcessed.toString())
                    put("lastImportRowsInserted", r.rowsInserted.toString())
                }
                put("dataPlane", "/api/v1/consumption/blob,/api/v1/consumptions")
            },
            configuration = configuration(),
            logSnapshot = processLog.snapshot()
        )
    }

    fun status(): ConsumptionBlobConnectorStatusResponse {
        val p = connectorsProperties.consumptionBlob
        val prefixes = p.resolvedBlobPrefixes()
        @Suppress("DEPRECATION")
        return ConsumptionBlobConnectorStatusResponse(
            id = id.pathId,
            enabled = p.enabled,
            configured = p.isConfigured(),
            storageAccountUrl = p.storageAccountUrl.ifBlank { null },
            container = p.container.ifBlank { null },
            blobPrefixes = prefixes,
            blobPrefix = prefixes.firstOrNull { it.isNotEmpty() },
            maxRangeDays = p.maxRangeDays,
            maxBlobsPerJob = p.maxBlobsPerJob,
            requireSourceRefId = p.requireSourceRefId,
            detail = info().detail
        )
    }

    override fun configuration(): Map<String, Any?> {
        val p = connectorsProperties.consumptionBlob
        val prefixes = p.resolvedBlobPrefixes()
        return linkedMapOf(
            "enabled" to p.enabled,
            "storageAccountUrl" to p.storageAccountUrl.ifBlank { null },
            "container" to p.container.ifBlank { null },
            "allowedBlobPrefixes" to prefixes.map { if (it.isEmpty()) "(root)" else it },
            "maxRangeDays" to p.maxRangeDays,
            "maxBlobsPerJob" to p.maxBlobsPerJob,
            "requireSourceRefId" to p.requireSourceRefId,
            // Runtime job selection (what start will load)
            "startDate" to jobStartDate.get()?.toString(),
            "endDate" to jobEndDate.get()?.toString(),
            "dryRun" to jobDryRun.get(),
            "blobPrefixes" to jobBlobPrefixes.get(),
            "dataPlane" to listOf("/api/v1/consumption/blob", "/api/v1/consumptions")
        )
    }

    override fun configure(updates: Map<String, Any?>): Map<String, Any?> {
        ConnectorConfigSupport.requireKnownKeys(
            updates,
            setOf("startDate", "endDate", "dryRun", "blobPrefixes")
        )
        if (running.get()) {
            throw BadRequestException(
                "Cannot reconfigure consumption-storage while an import is running. Stop first."
            )
        }
        ConnectorConfigSupport.optionalLocalDate(updates, "startDate")?.let { jobStartDate.set(it) }
        ConnectorConfigSupport.optionalLocalDate(updates, "endDate")?.let { jobEndDate.set(it) }
        if (updates.containsKey("startDate") && updates["startDate"] == null) {
            jobStartDate.set(null)
        }
        if (updates.containsKey("endDate") && updates["endDate"] == null) {
            jobEndDate.set(null)
        }
        ConnectorConfigSupport.optionalBoolean(updates, "dryRun")?.let { jobDryRun.set(it) }
        if (updates.containsKey("blobPrefixes")) {
            val list = ConnectorConfigSupport.optionalStringList(updates, "blobPrefixes")
            jobBlobPrefixes.set(list)
            if (list != null) {
                // Validate against allowed prefixes early
                resolveImportPrefixes(list, connectorsProperties.consumptionBlob.resolvedBlobPrefixes())
            }
        }
        processLog.info(
            "configuration updated startDate=${jobStartDate.get()} endDate=${jobEndDate.get()} " +
                "dryRun=${jobDryRun.get()} blobPrefixes=${jobBlobPrefixes.get()}"
        )
        return configuration()
    }

    /**
     * Start one import job using runtime job configuration ([configure]).
     * Optional [request] overrides job config for this run (used by data-plane helpers/tests).
     */
    override fun start(actor: String): ConnectorInfoResponse =
        start(actor, requestOverride = null)

    fun start(
        actor: String,
        requestOverride: ConsumptionBlobImportRequest?
    ): ConnectorInfoResponse {
        val props = connectorsProperties.consumptionBlob
        if (!props.enabled) {
            throw BadRequestException(
                "Consumption blob connector is disabled " +
                    "(set app.connectors.consumption-blob.enabled=true)"
            )
        }
        if (!props.isConfigured()) {
            throw BadRequestException(
                "Consumption blob connector is not configured " +
                    "(container + storage-account-url or connection-string required)"
            )
        }
        val request = requestOverride ?: buildRequestFromJobConfig()
        synchronized(lifecycleLock) {
            if (!running.compareAndSet(false, true)) {
                throw BadRequestException("Consumption blob import is already running")
            }
            cancelRequested.set(false)
            lastStartedBy.set(actor)
            lastStartedAt.set(UtcTimestamps.now())
            lastError.set(null)
        }
        processLog.info(
            "start by=$actor startDate=${request.startDate} endDate=${request.endDate} " +
                "dryRun=${request.dryRun} blobPrefixes=${request.blobPrefixes}"
        )
        try {
            val result = importRange(request, requestedBy = actor)
            lastImportResult.set(result)
            lastError.set(null)
            processLog.info(
                "import finished blobs=${result.blobsProcessed}/${result.blobsDiscovered} " +
                    "rowsInserted=${result.rowsInserted} dryRun=${result.dryRun}"
            )
        } catch (ex: Exception) {
            lastError.set(ex.message)
            processLog.error("import failed: ${ex.message}")
            throw if (ex is BadRequestException) ex
            else BadRequestException("Blob import failed: ${ex.message}")
        } finally {
            running.set(false)
            cancelRequested.set(false)
        }
        return info()
    }

    override fun stop(actor: String): ConnectorInfoResponse {
        if (!running.get()) {
            processLog.info("stop by=$actor (already stopped)")
            lastStoppedBy.set(actor)
            lastStoppedAt.set(UtcTimestamps.now())
            return info()
        }
        cancelRequested.set(true)
        lastStoppedBy.set(actor)
        lastStoppedAt.set(UtcTimestamps.now())
        processLog.info("stop by=$actor (cooperative cancel between blobs)")
        log.info("Consumption blob import cancel requested by={}", actor)
        return info()
    }

    /** Last import job result (used when viewing a date range that overlaps the job). */
    fun lastImportResult(): ConsumptionBlobImportResponse? = lastImportResult.get()

    /**
     * Data-plane view: list Avro blobs under configured prefixes for inclusive
     * [fromDate]..[untilDate] (UTC calendar days). Does not import.
     */
    fun viewRange(fromDate: LocalDate, untilDate: LocalDate): ConsumptionBlobViewResponse {
        val props = connectorsProperties.consumptionBlob
        if (!props.enabled) {
            throw BadRequestException(
                "Consumption blob connector is disabled " +
                    "(set app.connectors.consumption-blob.enabled=true)"
            )
        }
        if (!props.isConfigured()) {
            throw BadRequestException(
                "Consumption blob connector is not configured " +
                    "(container + storage-account-url or connection-string required)"
            )
        }
        if (untilDate.isBefore(fromDate)) {
            throw BadRequestException("untilDate must be on or after fromDate")
        }
        val rangeDays = ChronoUnit.DAYS.between(fromDate, untilDate) + 1
        if (rangeDays > props.maxRangeDays) {
            throw BadRequestException(
                "Date range spans $rangeDays days; max allowed is ${props.maxRangeDays}"
            )
        }

        val storage = storageClientProvider.getIfAvailable()
            ?: throw BadRequestException("Consumption blob storage client is not available")

        // Prefer runtime job prefixes when set; otherwise all configured roots.
        val rootPrefixes = resolveImportPrefixes(jobBlobPrefixes.get(), props.resolvedBlobPrefixes())
        val days = ConsumptionBlobPathSupport.daysInclusive(fromDate, untilDate)
        val listingPrefixes = ConsumptionBlobPathSupport.dayDirectoryPrefixes(rootPrefixes, days)

        val errors = mutableListOf<String>()
        val seenBlobNames = LinkedHashSet<String>()
        val blobs = mutableListOf<ConsumptionBlobObjectView>()

        for (prefix in listingPrefixes) {
            try {
                for (blob in storage.listAvroBlobs(prefix)) {
                    if (seenBlobNames.add(blob.name)) {
                        blobs += ConsumptionBlobObjectView(name = blob.name, size = blob.size)
                    }
                }
            } catch (ex: Exception) {
                val msg = "Failed listing prefix $prefix: ${ex.message}"
                log.error(msg, ex)
                errors += msg
            }
        }

        if (blobs.size > props.maxBlobsPerJob) {
            throw BadRequestException(
                "Discovered ${blobs.size} Avro blobs; max per request is ${props.maxBlobsPerJob}. " +
                    "Narrow fromDate/untilDate or raise app.connectors.consumption-blob.max-blobs-per-job."
            )
        }

        val last = lastImportResult.get()
        val lastMatching = last?.takeIf { importOverlapsRange(it, fromDate, untilDate) }

        return ConsumptionBlobViewResponse(
            fromDate = fromDate,
            untilDate = untilDate,
            blobPrefixes = rootPrefixes,
            daysVisited = days.size,
            blobCount = blobs.size,
            blobs = blobs,
            errors = errors.take(100),
            lastImport = lastMatching
        )
    }

    private fun importOverlapsRange(
        import: ConsumptionBlobImportResponse,
        fromDate: LocalDate,
        untilDate: LocalDate
    ): Boolean =
        !import.endDate.isBefore(fromDate) && !import.startDate.isAfter(untilDate)

    /**
     * Lists day folders in [startDate]..[endDate] under each configured (or requested) root
     * prefix, downloads matching Avro files, parses records, and optionally imports them via
     * [ConsumptionService.createFromImport].
     */
    fun importRange(request: ConsumptionBlobImportRequest, requestedBy: String): ConsumptionBlobImportResponse {
        val props = connectorsProperties.consumptionBlob
        if (!props.enabled) {
            throw BadRequestException(
                "Consumption blob connector is disabled " +
                    "(set app.connectors.consumption-blob.enabled=true)"
            )
        }
        if (!props.isConfigured()) {
            throw BadRequestException(
                "Consumption blob connector is not configured " +
                    "(container + storage-account-url or connection-string required)"
            )
        }
        val storage = storageClientProvider.getIfAvailable()
            ?: throw BadRequestException("Consumption blob storage client is not available")

        val start = request.startDate
        val end = request.endDate
        if (end.isBefore(start)) {
            throw BadRequestException("endDate must be on or after startDate")
        }
        val rangeDays = ChronoUnit.DAYS.between(start, end) + 1
        if (rangeDays > props.maxRangeDays) {
            throw BadRequestException(
                "Date range spans $rangeDays days; max allowed is ${props.maxRangeDays}"
            )
        }

        val rootPrefixes = resolveImportPrefixes(request.blobPrefixes, props.resolvedBlobPrefixes())

        val startedAt = UtcTimestamps.now()
        val days = ConsumptionBlobPathSupport.daysInclusive(start, end)
        val errors = mutableListOf<String>()
        var blobsDiscovered = 0
        var blobsProcessed = 0
        var blobsFailed = 0
        var rowsParsed = 0
        var rowsInserted = 0
        var rowsDuplicate = 0
        var rowsInvalid = 0
        var rowsFailed = 0
        var cancelled = false

        val listingPrefixes = ConsumptionBlobPathSupport.dayDirectoryPrefixes(rootPrefixes, days)
        val seenBlobNames = LinkedHashSet<String>()
        val blobQueue = mutableListOf<BlobObjectRef>()
        for (prefix in listingPrefixes) {
            if (cancelRequested.get()) {
                cancelled = true
                processLog.warn("cancel requested during listing")
                break
            }
            try {
                val listed = storage.listAvroBlobs(prefix)
                for (blob in listed) {
                    if (seenBlobNames.add(blob.name)) {
                        blobQueue += blob
                        blobsDiscovered++
                    }
                }
            } catch (ex: Exception) {
                val msg = "Failed listing prefix $prefix: ${ex.message}"
                log.error(msg, ex)
                processLog.error(msg)
                errors += msg
            }
        }

        if (!cancelled && blobQueue.size > props.maxBlobsPerJob) {
            throw BadRequestException(
                "Discovered ${blobQueue.size} Avro blobs; max per request is ${props.maxBlobsPerJob}. " +
                    "Narrow startDate/endDate or raise app.connectors.consumption-blob.max-blobs-per-job."
            )
        }

        for (blob in blobQueue) {
            if (cancelRequested.get()) {
                cancelled = true
                processLog.warn("cancel requested; remaining blobs skipped after ${blob.name}")
                break
            }
            try {
                storage.openBlob(blob.name).use { input ->
                    val records = avroFileReader.readAll(input, props.requireSourceRefId)
                    rowsParsed += records.size
                    for (parsed in records) {
                        if (request.dryRun) {
                            continue
                        }
                        try {
                            val result = consumptionService.createFromImport(
                                parsed.request,
                                parsed.externalId
                            )
                            if (result.created) rowsInserted++ else rowsDuplicate++
                        } catch (ex: BadRequestException) {
                            rowsInvalid++
                            errors += "${blob.name}: ${ex.message}"
                        } catch (ex: ResourceNotFoundException) {
                            rowsFailed++
                            errors += "${blob.name}: ${ex.message}"
                        } catch (ex: Exception) {
                            rowsFailed++
                            errors += "${blob.name}: ${ex.message}"
                            log.warn("Failed importing record from {}: {}", blob.name, ex.message)
                        }
                    }
                }
                blobsProcessed++
            } catch (ex: Exception) {
                blobsFailed++
                val msg = "Failed blob ${blob.name}: ${ex.message}"
                log.error(msg, ex)
                processLog.error(msg)
                errors += msg
            }
        }

        val finishedAt = UtcTimestamps.now()
        if (cancelled) {
            processLog.warn(
                "import cancelled by request blobsProcessed=$blobsProcessed discovered=$blobsDiscovered"
            )
        }
        log.info(
            "Blob import finished by={} start={} end={} dryRun={} prefixes={} blobs={}/{} " +
                "rows parsed={} inserted={} dup={} invalid={} failed={} cancelled={}",
            requestedBy,
            start,
            end,
            request.dryRun,
            rootPrefixes.joinToString(",") { p -> if (p.isEmpty()) "(root)" else p },
            blobsProcessed,
            blobsDiscovered,
            rowsParsed,
            rowsInserted,
            rowsDuplicate,
            rowsInvalid,
            rowsFailed,
            cancelled
        )

        return ConsumptionBlobImportResponse(
            startDate = start,
            endDate = end,
            dryRun = request.dryRun,
            requestedBy = requestedBy,
            startedAt = startedAt,
            finishedAt = finishedAt,
            blobPrefixes = rootPrefixes,
            daysVisited = days.size,
            blobsDiscovered = blobsDiscovered,
            blobsProcessed = blobsProcessed,
            blobsFailed = blobsFailed,
            rowsParsed = rowsParsed,
            rowsInserted = if (request.dryRun) 0 else rowsInserted,
            rowsDuplicate = if (request.dryRun) 0 else rowsDuplicate,
            rowsInvalid = rowsInvalid,
            rowsFailed = rowsFailed,
            errors = errors.take(100)
        )
    }

    private fun buildRequestFromJobConfig(): ConsumptionBlobImportRequest {
        val start = jobStartDate.get()
            ?: throw BadRequestException(
                "Configure startDate before start " +
                    "(PUT /api/v1/connectors/consumption-storage/config)"
            )
        val end = jobEndDate.get()
            ?: throw BadRequestException(
                "Configure endDate before start " +
                    "(PUT /api/v1/connectors/consumption-storage/config)"
            )
        return ConsumptionBlobImportRequest(
            startDate = start,
            endDate = end,
            dryRun = jobDryRun.get(),
            blobPrefixes = jobBlobPrefixes.get()
        )
    }

    private fun resolveImportPrefixes(
        requested: List<String>?,
        configured: List<String>
    ): List<String> {
        if (requested.isNullOrEmpty()) {
            return configured
        }
        val configuredSet = configured.toSet()
        val selected = LinkedHashSet<String>()
        for (raw in requested) {
            val normalized = ConsumptionBlobPathSupport.normalizeRootPrefix(raw)
            if (normalized !in configuredSet) {
                val allowed = configured.joinToString(", ") { p -> if (p.isEmpty()) "(root)" else p }
                throw BadRequestException(
                    "blobPrefixes entry '${if (raw.isBlank()) "(root)" else raw}' is not configured. " +
                        "Allowed: $allowed"
                )
            }
            selected += normalized
        }
        return selected.toList()
    }

    override fun health(): ConnectorHealthView {
        val i = info()
        return ConnectorHealthView(
            id = id,
            enabled = i.enabled,
            status = i.status,
            detail = i.detail,
            attributes = i.attributes + mapOf(
                "configured" to i.configured.toString(),
                "running" to i.running.toString()
            )
        )
    }
}
