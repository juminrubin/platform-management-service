package org.jrtech.platformmanagement.connectors.consumption.aggregate

import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobStorageClient
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.BlobFileClaimStore
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.ClaimOutcome
import org.jrtech.platformmanagement.connectors.runtime.ConnectorConfigSupport
import org.jrtech.platformmanagement.connectors.runtime.ConnectorLogBuffer
import org.jrtech.platformmanagement.connectors.runtime.ManagedConnector
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConnectorInfoResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.jobs.JobExecutor
import org.jrtech.platformmanagement.logging.logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Connector `daily-consumption-aggregate`: compact yesterday's 5-minute Parquet
 * files into one daily file. Start arms a daily UTC schedule; stop cancels it.
 */
@Service
@EnableConfigurationProperties(DailyConsumptionAggregateProperties::class)
class DailyConsumptionAggregateConnector(
    private val properties: DailyConsumptionAggregateProperties,
    private val connectorsProperties: ConnectorsProperties,
    private val storageClientProvider: ObjectProvider<ConsumptionBlobStorageClient>,
    private val claimStore: BlobFileClaimStore,
    private val taskScheduler: TaskScheduler
) : ManagedConnector {

    private val log = logger()
    private val processLog = ConnectorLogBuffer()
    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val jobInProgress = AtomicBoolean(false)
    private val scheduledFuture = AtomicReference<ScheduledFuture<*>?>(null)
    private val lastStartedBy = AtomicReference<String?>(null)
    private val lastStartedAt = AtomicReference<Instant?>(null)
    private val lastStoppedBy = AtomicReference<String?>(null)
    private val lastStoppedAt = AtomicReference<Instant?>(null)
    private val lastError = AtomicReference<String?>(null)
    private val lastResult = AtomicReference<DailyAggregateResult?>(null)
    private val targetDate = AtomicReference<LocalDate?>(null)
    private val jobForce = AtomicBoolean(false)
    private val runHourOverride = AtomicReference<Int?>(null)
    private val ownerId: String = run {
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("unknown")
        "daily-agg/$host/${UUID.randomUUID()}"
    }

    override val id: ConnectorId = ConnectorId.DAILY_CONSUMPTION_AGGREGATE

    override fun isEnabled(): Boolean = properties.enabled

    @EventListener(ApplicationReadyEvent::class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun autoStartIfConfigured() {
        if (properties.enabled && properties.autoStart) {
            try {
                start(actor = "SYSTEM")
                processLog.info("auto-start completed")
                log.info("Daily consumption aggregate auto-started")
            } catch (ex: Exception) {
                lastError.set(ex.message)
                processLog.error("auto-start failed: ${ex.message}")
                log.error("Daily consumption aggregate auto-start failed: {}", ex.message, ex)
            }
        }
    }

    override fun info(): ConnectorInfoResponse {
        val blob = connectorsProperties.consumptionBlob
        val isRunning = running.get()
        val statusLabel = when {
            !properties.enabled -> "DISABLED"
            !blob.isConfigured() -> "DOWN"
            jobInProgress.get() -> "RUNNING"
            lastError.get() != null -> "DEGRADED"
            isRunning -> "RUNNING"
            else -> "STOPPED"
        }
        val detail = when {
            !properties.enabled -> "disabled"
            !blob.isConfigured() -> "incomplete-config (consumption-blob storage required)"
            jobInProgress.get() -> "aggregate-in-progress"
            isRunning -> "scheduled"
            lastError.get() != null -> "last-error"
            lastResult.get() != null -> "idle-last-run-available"
            else -> "stopped"
        }
        return ConnectorInfoResponse(
            id = id.pathId,
            enabled = properties.enabled,
            configured = blob.isConfigured(),
            running = isRunning,
            status = statusLabel,
            detail = detail,
            lastStartedBy = lastStartedBy.get(),
            lastStartedAt = lastStartedAt.get(),
            lastStoppedBy = lastStoppedBy.get(),
            lastStoppedAt = lastStoppedAt.get(),
            lastError = lastError.get(),
            attributes = buildMap {
                lastResult.get()?.let { r ->
                    put("lastDay", r.day.toString())
                    put("lastSourceFiles", r.sourceFiles.toString())
                    put("lastRowsWritten", r.rowsWritten.toString())
                    r.outputBlob?.let { put("lastOutputBlob", it) }
                    r.skipReason?.let { put("lastSkipReason", it) }
                }
                put("dataPlane", "output container parquet")
            },
            configuration = configuration(),
            logSnapshot = processLog.snapshot()
        )
    }

    override fun configuration(): Map<String, Any?> {
        val blob = connectorsProperties.consumptionBlob
        return linkedMapOf(
            "enabled" to properties.enabled,
            "autoStart" to properties.autoStart,
            "runHourUtc" to effectiveRunHourUtc(),
            "targetDate" to targetDate.get()?.toString(),
            "force" to jobForce.get(),
            "outputContainer" to blob.outputContainer.ifBlank { null },
            "sourceBlobPrefix" to blob.resolvedOutputBlobPrefix().ifEmpty { "(root)" },
            "outputBlobPrefix" to properties.resolvedOutputBlobPrefix(blob.resolvedOutputBlobPrefix())
                .ifEmpty { "(root)" }
        )
    }

    override fun configure(updates: Map<String, Any?>): Map<String, Any?> {
        ConnectorConfigSupport.requireKnownKeys(updates, setOf("targetDate", "force", "runHourUtc"))
        if (jobInProgress.get()) {
            throw BadRequestException("Cannot reconfigure daily-consumption-aggregate while a run is in progress")
        }
        ConnectorConfigSupport.optionalLocalDate(updates, "targetDate")?.let { targetDate.set(it) }
        if (updates.containsKey("targetDate") && updates["targetDate"] == null) {
            targetDate.set(null)
        }
        ConnectorConfigSupport.optionalBoolean(updates, "force")?.let { jobForce.set(it) }
        ConnectorConfigSupport.optionalLong(updates, "runHourUtc")?.let { hour ->
            if (hour !in 0..23) {
                throw BadRequestException("runHourUtc must be 0..23")
            }
            runHourOverride.set(hour.toInt())
            if (running.get()) {
                scheduleNext()
            }
        }
        processLog.info(
            "configuration updated targetDate=${targetDate.get()} force=${jobForce.get()} " +
                "runHourUtc=${effectiveRunHourUtc()}"
        )
        return configuration()
    }

    override fun start(actor: String): ConnectorInfoResponse {
        if (!properties.enabled) {
            throw BadRequestException("Daily consumption aggregate is disabled")
        }
        if (!connectorsProperties.consumptionBlob.isConfigured()) {
            throw BadRequestException(
                "Daily consumption aggregate is not configured (consumption-blob storage required)"
            )
        }
        synchronized(lifecycleLock) {
            lastStartedBy.set(actor)
            lastStartedAt.set(UtcTimestamps.now())
            running.set(true)
            processLog.info("started by=$actor")
            log.info("Daily consumption aggregate started by={}", actor)
        }
        val day = targetDate.getAndSet(null) ?: yesterdayUtc()
        runDay(day, actor, jobForce.getAndSet(false))
        scheduleNext()
        return info()
    }

    override fun stop(actor: String): ConnectorInfoResponse {
        synchronized(lifecycleLock) {
            cancelSchedule()
            running.set(false)
            lastStoppedBy.set(actor)
            lastStoppedAt.set(UtcTimestamps.now())
            processLog.info("stopped by=$actor")
            log.info("Daily consumption aggregate stopped by={}", actor)
        }
        return info()
    }

    fun lastResult(): DailyAggregateResult? = lastResult.get()

    fun runDay(day: LocalDate, actor: String, force: Boolean = false): DailyAggregateResult {
        if (!jobInProgress.compareAndSet(false, true)) {
            throw BadRequestException("Daily aggregate is already running")
        }
        return try {
            val result = JobExecutor.submit { aggregateDay(day, actor, force) }.get()
            lastResult.set(result)
            if (result.error != null) {
                lastError.set(result.error)
            } else {
                lastError.set(null)
            }
            result
        } catch (ex: Exception) {
            val msg = ex.message ?: ex.javaClass.simpleName
            lastError.set(msg)
            processLog.error("aggregate failed: $msg")
            throw if (ex is BadRequestException) ex else BadRequestException("Daily aggregate failed: $msg")
        } finally {
            jobInProgress.set(false)
        }
    }

    private fun aggregateDay(day: LocalDate, actor: String, force: Boolean): DailyAggregateResult {
        val storage = storageClientProvider.getIfAvailable()
            ?: throw BadRequestException("Consumption blob storage client is not available")
        val blob = connectorsProperties.consumptionBlob
        val sourcePrefix = blob.resolvedOutputBlobPrefix()
        val job = DailyParquetAggregateJob(
            day = day,
            storage = storage,
            sourcePrefix = sourcePrefix,
            outputPrefix = properties.resolvedOutputBlobPrefix(sourcePrefix)
        )
        val sources = job.listSourceFiles()
        val fingerprint = job.fingerprint(sources)
        val outputName = job.outputBlobName()
        processLog.info(
            "aggregate day=$day by=$actor sources=${sources.size} force=$force output=$outputName"
        )
        if (sources.isEmpty()) {
            val empty = DailyAggregateResult(
                day = day,
                outputBlob = null,
                sourceFiles = 0,
                rowsWritten = 0,
                fingerprint = fingerprint,
                skipped = true,
                skipReason = "no-source-files"
            )
            processLog.info("no 5-minute parquet for $day")
            return empty
        }
        val claimKey = "daily-aggregate/$day"
        when (
            val outcome = claimStore.tryClaim(
                inputContainer = blob.outputContainer.trim(),
                inputBlob = claimKey,
                inputEtag = fingerprint,
                inputLength = sources.size.toLong(),
                outputBlob = outputName,
                owner = ownerId,
                lease = blob.resolvedClaimLease(),
                force = force
            )
        ) {
            is ClaimOutcome.AlreadySucceeded -> {
                processLog.info("skip already-succeeded $day")
                return DailyAggregateResult(
                    day = day,
                    outputBlob = outcome.claim.outputBlob.ifBlank { outputName },
                    sourceFiles = sources.size,
                    rowsWritten = outcome.claim.recordsWritten,
                    fingerprint = fingerprint,
                    skipped = true,
                    skipReason = "already-succeeded"
                )
            }
            is ClaimOutcome.HeldByOther -> {
                processLog.info("skip held-by-other $day owner=${outcome.claim.owner}")
                return DailyAggregateResult(
                    day = day,
                    outputBlob = null,
                    sourceFiles = sources.size,
                    rowsWritten = 0,
                    fingerprint = fingerprint,
                    skipped = true,
                    skipReason = "held-by-other"
                )
            }
            is ClaimOutcome.Acquired -> {
                val claim = outcome.claim
                if (!claimStore.renew(
                        claim.inputContainer, claim.inputBlob, claim.generation, claim.owner, blob.resolvedClaimLease()
                    )
                ) {
                    return DailyAggregateResult(
                        day = day,
                        outputBlob = null,
                        sourceFiles = sources.size,
                        rowsWritten = 0,
                        fingerprint = fingerprint,
                        skipped = true,
                        skipReason = "claim-lost"
                    )
                }
                return try {
                    val written = job.run()
                    claimStore.sealSucceeded(
                        claim.inputContainer,
                        claim.inputBlob,
                        claim.generation,
                        claim.owner,
                        written.outputBlob ?: outputName,
                        written.rowsWritten
                    )
                    processLog.info(
                        "aggregate finished day=$day files=${written.sourceFiles} rows=${written.rowsWritten}"
                    )
                    written
                } catch (ex: Exception) {
                    val msg = ex.message ?: ex.javaClass.simpleName
                    claimStore.markFailed(
                        claim.inputContainer, claim.inputBlob, claim.generation, claim.owner, msg
                    )
                    DailyAggregateResult(
                        day = day,
                        outputBlob = null,
                        sourceFiles = sources.size,
                        rowsWritten = 0,
                        fingerprint = fingerprint,
                        error = msg
                    )
                }
            }
        }
    }

    private fun scheduleNext() {
        cancelSchedule()
        if (!running.get()) return
        val next = nextRunInstant(UtcTimestamps.now(), effectiveRunHourUtc())
        val future = taskScheduler.schedule(
            {
                if (!running.get()) return@schedule
                try {
                    runDay(yesterdayUtc(), "SYSTEM-schedule", force = false)
                } catch (ex: Exception) {
                    log.error("Scheduled daily aggregate failed: {}", ex.message, ex)
                } finally {
                    if (running.get()) {
                        scheduleNext()
                    }
                }
            },
            next
        )
        scheduledFuture.set(future)
        processLog.info("next daily aggregate scheduled at $next")
    }

    private fun cancelSchedule() {
        scheduledFuture.getAndSet(null)?.cancel(false)
    }

    private fun effectiveRunHourUtc(): Int =
        runHourOverride.get() ?: properties.resolvedRunHourUtc()

    override fun health(): ConnectorHealthView {
        val i = info()
        return ConnectorHealthView(
            id = id,
            enabled = i.enabled,
            status = i.status,
            detail = i.detail,
            attributes = i.attributes
        )
    }

    companion object {
        fun yesterdayUtc(now: Instant = UtcTimestamps.now()): LocalDate =
            now.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)

        fun nextRunInstant(now: Instant, hourUtc: Int): Instant {
            val today = now.atZone(ZoneOffset.UTC).toLocalDate()
            var candidate = today.atTime(LocalTime.of(hourUtc.coerceIn(0, 23), 0)).toInstant(ZoneOffset.UTC)
            if (!candidate.isAfter(now)) {
                candidate = candidate.plus(Duration.ofDays(1))
            }
            return candidate
        }
    }
}
