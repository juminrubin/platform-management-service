package org.jrtech.platformmanagement.connectors.datasource

import org.jrtech.platformmanagement.cache.EntitlementCheckCache
import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.runtime.ConnectorConfigSupport
import org.jrtech.platformmanagement.connectors.runtime.ConnectorLogBuffer
import org.jrtech.platformmanagement.connectors.runtime.ManagedConnector
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConnectorInfoResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.logging.logger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Backend process that reloads the [EntitlementCheckCache] from the durable store
 * (Azure Table or in-memory).
 *
 * Catalog population is **out of band** (external scripts). This connector only
 * rebuilds the check index.
 *
 * Control plane: [ManagedConnector] under id `datasource-loading`.
 * Data plane: domain REST APIs + `GET|POST /api/v1/entitlements/cache*`.
 *
 * Start arms fixed-delay refresh (default 1 hour). Stop cancels the schedule only.
 */
@Service
@EnableConfigurationProperties(DatasourceLoadingProperties::class)
class DatasourceLoadingConnector(
    private val properties: DatasourceLoadingProperties,
    private val entitlementCheckCache: EntitlementCheckCache,
    private val taskScheduler: TaskScheduler
) : ManagedConnector {

    private val log = logger()
    private val processLog = ConnectorLogBuffer()
    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val scheduledFuture = AtomicReference<ScheduledFuture<*>?>(null)
    private val lastStartedBy = AtomicReference<String?>(null)
    private val lastStartedAt = AtomicReference<Instant?>(null)
    private val lastStoppedBy = AtomicReference<String?>(null)
    private val lastStoppedAt = AtomicReference<Instant?>(null)
    private val lastError = AtomicReference<String?>(null)
    private val refreshIntervalOverrideMs = AtomicReference<Long?>(null)

    override val id: ConnectorId = ConnectorId.DATASOURCE_LOADING

    override fun isEnabled(): Boolean = properties.enabled

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

    @EventListener(ApplicationReadyEvent::class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun autoStartIfConfigured() {
        if (properties.enabled && properties.autoStart) {
            try {
                start(actor = "SYSTEM")
                processLog.info("auto-start completed")
                log.info("Datasource loading connector auto-started")
            } catch (ex: Exception) {
                lastError.set(ex.message)
                processLog.error("auto-start failed: ${ex.message}")
                log.error("Datasource loading auto-start failed: {}", ex.message, ex)
            }
        }
    }

    override fun info(): ConnectorInfoResponse {
        val cache = entitlementCheckCache.status()
        val isRunning = running.get()
        val err = cache.lastError ?: lastError.get()
        val detail = when {
            !properties.enabled -> "disabled"
            cache.refreshInProgress -> "refresh-in-progress"
            isRunning && err != null -> "running-with-error"
            isRunning && !cache.loaded -> "running-not-loaded"
            isRunning -> "running"
            err != null -> "last-error"
            !cache.loaded -> "not-loaded"
            else -> "stopped"
        }
        val statusLabel = when {
            !properties.enabled -> "DISABLED"
            cache.refreshInProgress || isRunning -> "RUNNING"
            err != null -> "DEGRADED"
            else -> "STOPPED"
        }
        return ConnectorInfoResponse(
            id = id.pathId,
            enabled = properties.enabled,
            configured = true,
            running = isRunning,
            status = statusLabel,
            detail = detail,
            lastStartedBy = lastStartedBy.get(),
            lastStartedAt = lastStartedAt.get(),
            lastStoppedBy = lastStoppedBy.get(),
            lastStoppedAt = lastStoppedAt.get(),
            lastError = err,
            attributes = buildMap {
                put("refreshInProgress", cache.refreshInProgress.toString())
                put("serviceCount", cache.serviceCount.toString())
                put("callerCount", cache.callerCount.toString())
                put("entitlementCount", cache.entitlementCount.toString())
                cache.loadedAt?.let { put("cacheLoadedAt", it.toString()) }
                cache.entitlementsAsOf?.let { put("entitlementsAsOf", it.toString()) }
                cache.lastRefreshBy?.let { put("lastRefreshBy", it) }
                put("dataPlane", "/api/v1/entitlements/cache,/api/v1/entitlements/check")
            },
            configuration = configuration(),
            logSnapshot = processLog.snapshot()
        )
    }

    override fun configuration(): Map<String, Any?> = mapOf(
        "enabled" to properties.enabled,
        "autoStart" to properties.autoStart,
        "refreshIntervalMs" to effectiveRefreshIntervalMs(),
        "refreshIntervalOverrideMs" to refreshIntervalOverrideMs.get()
    )

    override fun configure(updates: Map<String, Any?>): Map<String, Any?> {
        ConnectorConfigSupport.requireKnownKeys(updates, setOf("refreshIntervalMs"))
        ConnectorConfigSupport.optionalLong(updates, "refreshIntervalMs")?.let { ms ->
            if (ms < 5_000L) {
                throw BadRequestException("refreshIntervalMs must be >= 5000")
            }
            refreshIntervalOverrideMs.set(ms)
            processLog.info("configured refreshIntervalMs=$ms")
            if (running.get()) {
                reschedule()
            }
        }
        return configuration()
    }

    override fun start(actor: String): ConnectorInfoResponse {
        if (!properties.enabled) {
            throw BadRequestException("Datasource loading connector is disabled")
        }
        synchronized(lifecycleLock) {
            if (running.get()) {
                processLog.info("start ignored; already running (actor=$actor)")
                return info()
            }
            lastStartedBy.set(actor)
            lastStartedAt.set(UtcTimestamps.now())
            running.set(true)
            processLog.info("started by=$actor")
            log.info("Datasource loading connector started by={}", actor)
            runLoadCycle(triggeredBy = actor)
            scheduleNext()
        }
        return info()
    }

    override fun stop(actor: String): ConnectorInfoResponse {
        synchronized(lifecycleLock) {
            cancelSchedule()
            running.set(false)
            lastStoppedBy.set(actor)
            lastStoppedAt.set(UtcTimestamps.now())
            processLog.info("stopped by=$actor")
            log.info("Datasource loading connector stopped by={}", actor)
        }
        return info()
    }

    /**
     * Rebuild entitlement check cache from the durable store.
     * Does **not** load or seed `datasource.json`.
     */
    fun runLoadCycle(triggeredBy: String) {
        try {
            processLog.info("refreshing entitlement check cache (by=$triggeredBy)")
            entitlementCheckCache.refresh(triggeredBy = triggeredBy)
            lastError.set(null)
            processLog.info(
                "load cycle complete services=${entitlementCheckCache.status().serviceCount} " +
                    "callers=${entitlementCheckCache.status().callerCount} " +
                    "entitlements=${entitlementCheckCache.status().entitlementCount}"
            )
        } catch (ex: Exception) {
            val msg = ex.message ?: ex.javaClass.simpleName
            lastError.set(msg)
            processLog.error("load cycle failed: $msg")
            log.error("Datasource load cycle failed: {}", msg, ex)
            throw ex
        }
    }

    private fun scheduleNext() {
        cancelSchedule()
        if (!running.get()) return
        val delay = Duration.ofMillis(effectiveRefreshIntervalMs())
        val future = taskScheduler.schedule(
            {
                if (!running.get()) return@schedule
                try {
                    runLoadCycle(triggeredBy = "SYSTEM-schedule")
                } catch (_: Exception) {
                    // logged in runLoadCycle
                } finally {
                    if (running.get()) {
                        scheduleNext()
                    }
                }
            },
            Instant.now().plus(delay)
        )
        scheduledFuture.set(future)
        processLog.info("next refresh scheduled in ${delay.toMillis()}ms")
    }

    private fun reschedule() {
        synchronized(lifecycleLock) {
            if (running.get()) {
                scheduleNext()
            }
        }
    }

    private fun cancelSchedule() {
        scheduledFuture.getAndSet(null)?.cancel(false)
    }

    private fun effectiveRefreshIntervalMs(): Long =
        refreshIntervalOverrideMs.get() ?: properties.refreshIntervalMs
}
