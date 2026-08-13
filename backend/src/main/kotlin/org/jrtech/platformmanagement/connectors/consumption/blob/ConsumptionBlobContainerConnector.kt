package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.BlobFileClaimStore
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.ClaimOutcome
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ConsumptionBlobFilePipeline
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.FilePipelineResult
import org.jrtech.platformmanagement.jobs.JobExecutor
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
import org.jrtech.platformmanagement.logging.logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runner for Avro → Parquet consumption pipelines.
 *
 * Control plane: [ManagedConnector] — configure date range / prefixes, start/stop job.
 * Data plane: blob view by date range (`GET /api/v1/consumption/blob`).
 *
 * [start] lists Avro files and submits **one non-Spring pipeline per file** to the
 * process-wide [JobExecutor]. Pipelines are constructed with
 * `ConsumptionBlobFilePipeline.create`.
 */
@Service
class ConsumptionBlobContainerConnector(
    private val connectorsProperties: ConnectorsProperties,
    private val storageClientProvider: ObjectProvider<ConsumptionBlobStorageClient>,
    private val claimStore: BlobFileClaimStore
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

    private val jobStartDate = AtomicReference<LocalDate?>(null)
    private val jobEndDate = AtomicReference<LocalDate?>(null)
    private val jobDryRun = AtomicBoolean(false)
    private val jobForce = AtomicBoolean(false)
    private val jobInputBlobPrefixes = AtomicReference<List<String>?>(null)
    private val ownerId: String = run {
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("unknown")
        "$host/${UUID.randomUUID()}"
    }

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
            !p.isConfigured() ->
                "incomplete-config (need input-container + output-container + storage-account-name or connection-string)"
            isRunning -> "pipelines-in-progress"
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
                    put("lastImportRecordsWritten", r.recordsWritten.toString())
                    put("lastImportOutputFiles", r.outputFiles.toString())
                }
                put("dataPlane", "/api/v1/consumption/blob")
            },
            configuration = configuration(),
            logSnapshot = processLog.snapshot()
        )
    }

    fun status(): ConsumptionBlobConnectorStatusResponse {
        val p = connectorsProperties.consumptionBlob
        return ConsumptionBlobConnectorStatusResponse(
            id = id.pathId,
            enabled = p.enabled,
            configured = p.isConfigured(),
            storageAccountName = p.storageAccountName.ifBlank { null },
            inputContainer = p.inputContainer.ifBlank { null },
            outputContainer = p.outputContainer.ifBlank { null },
            objectType = p.resolvedObjectType(),
            inputBlobPrefixes = p.resolvedInputBlobPrefixes(),
            outputBlobPrefix = p.resolvedOutputBlobPrefix(),
            maxRangeDays = p.maxRangeDays,
            maxBlobsPerJob = p.maxBlobsPerJob,
            requireSourceRefId = p.requireSourceRefId,
            detail = info().detail
        )
    }

    override fun configuration(): Map<String, Any?> {
        val p = connectorsProperties.consumptionBlob
        return linkedMapOf(
            "enabled" to p.enabled,
            "storageAccountName" to p.storageAccountName.ifBlank { null },
            "inputContainer" to p.inputContainer.ifBlank { null },
            "outputContainer" to p.outputContainer.ifBlank { null },
            "inputBlobPrefixes" to p.resolvedInputBlobPrefixes().map { if (it.isEmpty()) "(root)" else it },
            "outputBlobPrefix" to p.resolvedOutputBlobPrefix().ifEmpty { "(root)" },
            "jobInputBlobPrefixes" to jobInputBlobPrefixes.get(),
            "objectType" to p.resolvedObjectType(),
            "maxConcurrentPipelines" to p.resolvedMaxConcurrentPipelines(),
            "jobExecutorPoolSize" to JobExecutor.poolSize(),
            "jobExecutorRunning" to JobExecutor.isRunning(),
            "maxRangeDays" to p.maxRangeDays,
            "maxBlobsPerJob" to p.maxBlobsPerJob,
            "startDate" to jobStartDate.get()?.toString(),
            "endDate" to jobEndDate.get()?.toString(),
            "dryRun" to jobDryRun.get(),
            "force" to jobForce.get(),
            "dataPlane" to listOf("/api/v1/consumption/blob")
        )
    }

    override fun configure(updates: Map<String, Any?>): Map<String, Any?> {
        ConnectorConfigSupport.requireKnownKeys(
            updates,
            setOf("startDate", "endDate", "dryRun", "force", "inputBlobPrefixes")
        )
        if (running.get()) {
            throw BadRequestException(
                "Cannot reconfigure consumption-storage while pipelines are running. Stop first."
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
        ConnectorConfigSupport.optionalBoolean(updates, "force")?.let { jobForce.set(it) }
        if (updates.containsKey("inputBlobPrefixes")) {
            val list = ConnectorConfigSupport.optionalStringList(updates, "inputBlobPrefixes")
            jobInputBlobPrefixes.set(list)
            if (list != null) {
                resolveInputPrefixes(list, connectorsProperties.consumptionBlob.resolvedInputBlobPrefixes())
            }
        }
        processLog.info(
            "configuration updated startDate=${jobStartDate.get()} endDate=${jobEndDate.get()} " +
                "dryRun=${jobDryRun.get()} force=${jobForce.get()} inputBlobPrefixes=${jobInputBlobPrefixes.get()}"
        )
        return configuration()
    }

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
                    "(input-container + output-container + storage-account-name or connection-string required)"
            )
        }
        val request = requestOverride ?: buildRequestFromJobConfig()
        synchronized(lifecycleLock) {
            if (!running.compareAndSet(false, true)) {
                throw BadRequestException("Consumption blob pipelines are already running")
            }
            cancelRequested.set(false)
            lastStartedBy.set(actor)
            lastStartedAt.set(UtcTimestamps.now())
            lastError.set(null)
        }
        processLog.info(
            "start by=$actor startDate=${request.startDate} endDate=${request.endDate} " +
                "dryRun=${request.dryRun} force=${request.force} inputBlobPrefixes=${request.inputBlobPrefixes}"
        )
        try {
            val result = processRange(request, requestedBy = actor)
            lastImportResult.set(result)
            lastError.set(null)
            processLog.info(
                "pipelines finished blobs=${result.blobsProcessed}/${result.blobsDiscovered} " +
                    "recordsWritten=${result.recordsWritten} outputFiles=${result.outputFiles} " +
                    "dryRun=${result.dryRun}"
            )
        } catch (ex: Exception) {
            lastError.set(ex.message)
            processLog.error("pipelines failed: ${ex.message}")
            throw if (ex is BadRequestException) ex
            else BadRequestException("Blob pipeline failed: ${ex.message}")
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
        processLog.info("stop by=$actor (cooperative cancel between files)")
        log.info("Consumption blob pipeline cancel requested by={}", actor)
        return info()
    }

    fun lastImportResult(): ConsumptionBlobImportResponse? = lastImportResult.get()

    /**
     * Data-plane view: list Avro blobs under configured prefixes for inclusive
     * [fromDate]..[untilDate] (UTC calendar days). Does not run pipelines.
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
                    "(input-container + output-container + storage-account-name or connection-string required)"
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

        val inputPrefixes = resolveInputPrefixes(
            jobInputBlobPrefixes.get(),
            props.resolvedInputBlobPrefixes()
        )
        val days = ConsumptionBlobPathSupport.daysInclusive(fromDate, untilDate)
        val listingPrefixes = ConsumptionBlobPathSupport.dayDirectoryPrefixes(inputPrefixes, days)

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
            inputBlobPrefixes = inputPrefixes,
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
     * Lists Avro files in [startDate]..[endDate] and runs one pipeline per file
     * on a bounded thread pool. Pipelines are **not** Spring beans.
     */
    fun processRange(request: ConsumptionBlobImportRequest, requestedBy: String): ConsumptionBlobImportResponse {
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
                    "(input-container + output-container + storage-account-name or connection-string required)"
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

        val inputPrefixes = resolveInputPrefixes(
            request.inputBlobPrefixes,
            props.resolvedInputBlobPrefixes()
        )
        val outputPrefix = props.resolvedOutputBlobPrefix()
        val startedAt = UtcTimestamps.now()
        val days = ConsumptionBlobPathSupport.daysInclusive(start, end)
        val errors = mutableListOf<String>()
        val listingPrefixes = ConsumptionBlobPathSupport.dayDirectoryPrefixes(inputPrefixes, days)
        val seenBlobNames = LinkedHashSet<String>()
        val blobQueue = mutableListOf<BlobObjectRef>()
        var cancelled = false

        for (prefix in listingPrefixes) {
            if (cancelRequested.get()) {
                cancelled = true
                processLog.warn("cancel requested during listing")
                break
            }
            try {
                for (blob in storage.listAvroBlobs(prefix)) {
                    if (seenBlobNames.add(blob.name)) {
                        blobQueue += blob
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

        val pipelineResults = if (blobQueue.isEmpty() || cancelled) {
            emptyList()
        } else {
            runPipelines(
                blobQueue,
                storage,
                request.dryRun,
                request.force,
                props.resolvedMaxConcurrentPipelines(),
                inputPrefixes,
                outputPrefix
            )
        }

        var blobsProcessed = 0
        var blobsFailed = 0
        var blobsSkipped = 0
        var recordsRead = 0
        var recordsMatched = 0
        var recordsWritten = 0
        var recordsInvalid = 0
        var outputFiles = 0
        for (result in pipelineResults) {
            if (result.cancelled) {
                cancelled = true
            }
            when {
                result.skipped -> blobsSkipped++
                result.error != null -> {
                    blobsFailed++
                    errors += "${result.inputBlob}: ${result.error}"
                    processLog.error("pipeline ${result.inputBlob}: ${result.error}")
                }
                else -> {
                    blobsProcessed++
                    recordsRead += result.recordsRead
                    recordsMatched += result.recordsMatched
                    recordsWritten += result.recordsWritten
                    recordsInvalid += result.recordsInvalid
                    if (result.outputBlob != null) outputFiles++
                }
            }
        }

        val finishedAt = UtcTimestamps.now()
        if (cancelled) {
            processLog.warn(
                "pipelines cancelled blobsProcessed=$blobsProcessed discovered=${blobQueue.size}"
            )
        }
        log.info(
            "Blob pipelines finished by={} start={} end={} dryRun={} inputPrefixes={} outputPrefix={} blobs={}/{} " +
                "records matched={} written={} outputFiles={} cancelled={}",
            requestedBy,
            start,
            end,
            request.dryRun,
            inputPrefixes.joinToString(",") { p -> if (p.isEmpty()) "(root)" else p },
            outputPrefix.ifEmpty { "(root)" },
            blobsProcessed,
            blobQueue.size,
            recordsMatched,
            recordsWritten,
            outputFiles,
            cancelled
        )

        return ConsumptionBlobImportResponse(
            startDate = start,
            endDate = end,
            dryRun = request.dryRun,
            requestedBy = requestedBy,
            startedAt = startedAt,
            finishedAt = finishedAt,
            inputBlobPrefixes = inputPrefixes,
            outputBlobPrefix = outputPrefix,
            daysVisited = days.size,
            blobsDiscovered = blobQueue.size,
            blobsProcessed = blobsProcessed,
            blobsFailed = blobsFailed,
            blobsSkipped = blobsSkipped,
            recordsRead = recordsRead,
            recordsMatched = recordsMatched,
            recordsWritten = if (request.dryRun) 0 else recordsWritten,
            recordsInvalid = recordsInvalid,
            outputFiles = if (request.dryRun) 0 else outputFiles,
            errors = errors.take(100)
        )
    }

    /**
     * Instantiates one [ConsumptionBlobFilePipeline] per Avro file (not a Spring bean)
     * and submits each to the process-wide [JobExecutor].
     *
     * [maxConcurrent] only caps how many blob pipelines this job submits at once
     * so a large date range cannot fill the shared pool by itself.
     */
    private fun runPipelines(
        blobs: List<BlobObjectRef>,
        storage: ConsumptionBlobStorageClient,
        dryRun: Boolean,
        force: Boolean,
        maxConcurrent: Int,
        inputPrefixes: List<String>,
        outputPrefix: String
    ): List<FilePipelineResult> {
        val props = connectorsProperties.consumptionBlob
        val objectType = props.resolvedObjectType()
        val inputContainer = props.inputContainer.trim()
        val lease = props.resolvedClaimLease()
        val results = ArrayList<FilePipelineResult>(blobs.size)
        for (batch in blobs.chunked(maxConcurrent.coerceAtLeast(1))) {
            if (cancelRequested.get()) break
            val futures = ArrayList<Future<FilePipelineResult>>(batch.size)
            for (blob in batch) {
                val outputName = ConsumptionBlobPathSupport.parquetOutputName(
                    blob.name, inputPrefixes, outputPrefix
                )
                if (dryRun) {
                    futures += JobExecutor.submit {
                        runFilePipeline(
                            blob = blob,
                            storage = storage,
                            objectType = objectType,
                            dryRun = true,
                            inputPrefixes = inputPrefixes,
                            outputPrefix = outputPrefix,
                            claim = null
                        )
                    }
                    continue
                }
                val etag = blob.etag.ifBlank { "len:${blob.size ?: -1}:${blob.name}" }
                when (
                    val outcome = claimStore.tryClaim(
                        inputContainer = inputContainer,
                        inputBlob = blob.name,
                        inputEtag = etag,
                        inputLength = blob.size,
                        outputBlob = outputName,
                        owner = ownerId,
                        lease = lease,
                        force = force
                    )
                ) {
                    is ClaimOutcome.AlreadySucceeded -> {
                        processLog.info("skip already-succeeded ${blob.name}")
                        results += FilePipelineResult.skipped(blob.name, "already-succeeded")
                    }
                    is ClaimOutcome.HeldByOther -> {
                        processLog.info("skip held-by-other ${blob.name} owner=${outcome.claim.owner}")
                        results += FilePipelineResult.skipped(blob.name, "held-by-other")
                    }
                    is ClaimOutcome.Acquired -> {
                        val claim = outcome.claim
                        futures += JobExecutor.submit {
                            runFilePipeline(
                                blob = blob,
                                storage = storage,
                                objectType = objectType,
                                dryRun = false,
                                inputPrefixes = inputPrefixes,
                                outputPrefix = outputPrefix,
                                claim = claim
                            )
                        }
                    }
                }
            }
            results += futures.map { future -> awaitPipeline(future) }
        }
        return results
    }

    private fun runFilePipeline(
        blob: BlobObjectRef,
        storage: ConsumptionBlobStorageClient,
        objectType: String,
        dryRun: Boolean,
        inputPrefixes: List<String>,
        outputPrefix: String,
        claim: org.jrtech.platformmanagement.connectors.consumption.blob.claim.BlobFileClaim?
    ): FilePipelineResult {
        if (claim != null) {
            val renewed = claimStore.renew(
                inputContainer = claim.inputContainer,
                inputBlob = claim.inputBlob,
                generation = claim.generation,
                owner = claim.owner,
                lease = connectorsProperties.consumptionBlob.resolvedClaimLease()
            )
            if (!renewed) {
                return FilePipelineResult.skipped(blob.name, "claim-lost")
            }
        }
        val etag = blob.etag.ifBlank { "len:${blob.size ?: -1}:${blob.name}" }
        val result = ConsumptionBlobFilePipeline.create(
            inputBlobName = blob.name,
            storage = storage,
            objectType = objectType,
            dryRun = dryRun,
            cancelRequested = { cancelRequested.get() },
            inputPrefixes = inputPrefixes,
            outputPrefix = outputPrefix,
            outputMetadata = mapOf(
                "input_name" to blob.name,
                "input_etag" to etag,
                "input_length" to (blob.size?.toString() ?: "")
            )
        ).run()
        if (claim == null) return result
        if (result.error != null) {
            claimStore.markFailed(
                inputContainer = claim.inputContainer,
                inputBlob = claim.inputBlob,
                generation = claim.generation,
                owner = claim.owner,
                error = result.error
            )
            return result
        }
        if (result.cancelled) {
            return result
        }
        if (result.outputBlob != null) {
            val sealed = claimStore.sealSucceeded(
                inputContainer = claim.inputContainer,
                inputBlob = claim.inputBlob,
                generation = claim.generation,
                owner = claim.owner,
                outputBlob = result.outputBlob,
                recordsWritten = result.recordsWritten
            )
            if (!sealed) {
                processLog.warn("seal lost for ${blob.name} generation=${claim.generation}")
            }
        }
        return result
    }

    private fun awaitPipeline(future: Future<FilePipelineResult>): FilePipelineResult =
        try {
            future.get()
        } catch (ex: Exception) {
            FilePipelineResult(
                inputBlob = "unknown",
                outputBlob = null,
                recordsRead = 0,
                recordsMatched = 0,
                recordsWritten = 0,
                recordsInvalid = 0,
                error = ex.message ?: ex.javaClass.simpleName
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
            inputBlobPrefixes = jobInputBlobPrefixes.get(),
            force = jobForce.get()
        )
    }

    private fun resolveInputPrefixes(
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
                    "inputBlobPrefixes entry '${if (raw.isBlank()) "(root)" else raw}' is not configured. " +
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
