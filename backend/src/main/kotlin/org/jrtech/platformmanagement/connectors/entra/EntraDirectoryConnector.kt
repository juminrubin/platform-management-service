package org.jrtech.platformmanagement.connectors.entra

import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.config.azure.AzureCredentialFactory
import org.jrtech.platformmanagement.config.azure.AzureCredentialProperties
import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.runtime.ConnectorConfigSupport
import org.jrtech.platformmanagement.connectors.runtime.ConnectorLogBuffer
import org.jrtech.platformmanagement.connectors.runtime.ManagedConnector
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConnectorInfoResponse
import org.jrtech.platformmanagement.dto.EntraDirectoryConnectorStatusResponse
import org.jrtech.platformmanagement.entra.EntraGroupDirectoryService
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.logging.logger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Backend process for loading Entra Platform-System-* groups/members via Microsoft Graph.
 *
 * Control plane: [ManagedConnector] (info / configure / start / stop).
 * Data plane: `/api/v1/entra/groups` and `/api/v1/entra/members`.
 *
 * Start arms the process (immediate load + fixed-delay schedule).
 * Stop disarms pending ticks only — in-flight Graph loads complete.
 */
@Service
class EntraDirectoryConnector(
    private val properties: EntraDirectoryProperties,
    private val azureCredential: AzureCredentialProperties,
    private val directoryService: EntraGroupDirectoryService,
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

    /** Runtime override of refresh interval; null → use deploy-time property. */
    private val refreshIntervalOverrideMs = AtomicReference<Long?>(null)

    override val id: ConnectorId = ConnectorId.ENTRA_DIRECTORY

    override fun isEnabled(): Boolean = properties.enabled

    @EventListener(ApplicationReadyEvent::class)
    fun autoStartIfConfigured() {
        if (properties.enabled && properties.autoStart) {
            try {
                start(actor = "SYSTEM")
                processLog.info("auto-start completed (app.entra-directory.auto-start=true)")
                log.info("Entra directory connector auto-started")
            } catch (ex: Exception) {
                lastError.set(ex.message)
                processLog.error("auto-start failed: ${ex.message}")
                log.error("Entra directory auto-start failed: {}", ex.message, ex)
            }
        }
    }

    override fun info(): ConnectorInfoResponse {
        val snap = directoryService.snapshot()
        val configured = directoryService.hasGraphClient()
        val inProgress = directoryService.isRefreshInProgress()
        val isRunning = running.get()
        val err = directoryService.lastError() ?: lastError.get()
        val detail = when {
            !properties.enabled -> "disabled"
            inProgress -> "refresh-in-progress"
            isRunning && err != null -> "running-with-error"
            isRunning && snap.loadedAt == null -> "running-not-loaded"
            isRunning -> "running"
            err != null -> "last-error"
            snap.loadedAt == null -> "not-loaded"
            else -> "stopped"
        }
        val statusLabel = when {
            !properties.enabled -> "DISABLED"
            !configured && properties.enabled -> "DOWN"
            inProgress || isRunning -> "RUNNING"
            err != null -> "DEGRADED"
            else -> "STOPPED"
        }
        return ConnectorInfoResponse(
            id = id.pathId,
            enabled = properties.enabled,
            configured = configured,
            running = isRunning,
            status = statusLabel,
            detail = detail,
            lastStartedBy = lastStartedBy.get(),
            lastStartedAt = lastStartedAt.get(),
            lastStoppedBy = lastStoppedBy.get(),
            lastStoppedAt = lastStoppedAt.get(),
            lastError = err,
            attributes = buildMap {
                put("refreshInProgress", inProgress.toString())
                put("groupCount", snap.groupCount.toString())
                put("memberCount", snap.memberCount.toString())
                put("uniqueMemberCount", directoryService.allMembers().size.toString())
                directoryService.lastLoadedAt()?.let { put("lastLoadedAt", it.toString()) }
                directoryService.lastRefreshBy()?.let { put("lastRefreshBy", it) }
                directoryService.lastRefreshStartedAt()?.let { put("lastRefreshStartedAt", it.toString()) }
                directoryService.lastRefreshFinishedAt()?.let { put("lastRefreshFinishedAt", it.toString()) }
                put("dataPlane", "/api/v1/entra/groups,/api/v1/entra/members")
            },
            configuration = configuration(),
            logSnapshot = processLog.snapshot()
        )
    }

    /** Typed status for unit tests / backward compatibility. */
    fun status(): EntraDirectoryConnectorStatusResponse {
        val i = info()
        val snap = directoryService.snapshot()
        return EntraDirectoryConnectorStatusResponse(
            id = i.id,
            enabled = i.enabled,
            configured = i.configured,
            running = i.running,
            autoStart = properties.autoStart,
            refreshIntervalMs = effectiveRefreshIntervalMs(),
            groupNamePrefix = properties.groupNamePrefix,
            includeTransitiveMembers = properties.includeTransitiveMembers,
            refreshInProgress = directoryService.isRefreshInProgress(),
            lastLoadedAt = directoryService.lastLoadedAt(),
            lastRefreshStartedAt = directoryService.lastRefreshStartedAt(),
            lastRefreshFinishedAt = directoryService.lastRefreshFinishedAt(),
            lastRefreshBy = directoryService.lastRefreshBy(),
            lastStartedBy = i.lastStartedBy,
            lastStartedAt = i.lastStartedAt,
            lastStoppedBy = i.lastStoppedBy,
            lastStoppedAt = i.lastStoppedAt,
            lastError = i.lastError,
            groupCount = snap.groupCount,
            memberCount = snap.memberCount,
            uniqueMemberCount = directoryService.allMembers().size,
            detail = i.detail
        )
    }

    override fun configuration(): Map<String, Any?> = linkedMapOf(
        "enabled" to properties.enabled,
        "autoStart" to properties.autoStart,
        "groupNamePrefix" to properties.groupNamePrefix,
        "includeTransitiveMembers" to properties.includeTransitiveMembers,
        "refreshIntervalMs" to effectiveRefreshIntervalMs(),
        "azureCredentialMode" to AzureCredentialFactory.resolve(azureCredential).name,
        "clientIdConfigured" to azureCredential.hasClientId(),
        "servicePrincipalConfigured" to azureCredential.hasServicePrincipalPair(),
        "tenantIdConfigured" to azureCredential.tenantIdOrEmpty().isNotEmpty(),
        "dataPlane" to listOf("/api/v1/entra/groups", "/api/v1/entra/members")
    )

    override fun configure(updates: Map<String, Any?>): Map<String, Any?> {
        ConnectorConfigSupport.requireKnownKeys(updates, setOf("refreshIntervalMs"))
        ConnectorConfigSupport.optionalLong(updates, "refreshIntervalMs")?.let { ms ->
            if (ms < 0L) {
                throw BadRequestException("configuration.refreshIntervalMs must be >= 0")
            }
            refreshIntervalOverrideMs.set(ms)
            processLog.info("configuration refreshIntervalMs=$ms")
            synchronized(lifecycleLock) {
                if (running.get()) {
                    armSchedule()
                    processLog.info("schedule re-armed after refreshIntervalMs change")
                }
            }
        }
        return configuration()
    }

    override fun start(actor: String): ConnectorInfoResponse {
        if (!properties.enabled) {
            throw BadRequestException(
                "Entra directory connector is disabled " +
                    "(set app.entra-directory.enabled=true)"
            )
        }
        synchronized(lifecycleLock) {
            if (running.get()) {
                processLog.info("start ignored (already running) by=$actor")
                return info()
            }
            running.set(true)
            lastStartedBy.set(actor)
            lastStartedAt.set(UtcTimestamps.now())
            lastError.set(null)
            processLog.info("start by=$actor")
            try {
                val snap = directoryService.refresh(triggeredBy = actor)
                processLog.info(
                    "Graph load ok groups=${snap.groupCount} members=${snap.memberCount} by=$actor"
                )
                log.info("Entra directory connector started by={}", actor)
            } catch (ex: Exception) {
                lastError.set(ex.message)
                processLog.error("Graph load failed on start by=$actor: ${ex.message}")
                log.error("Entra directory initial refresh failed on start by={}", actor, ex)
                if (ex is BadRequestException) throw ex
            }
            armSchedule()
        }
        return info()
    }

    override fun stop(actor: String): ConnectorInfoResponse {
        synchronized(lifecycleLock) {
            running.set(false)
            scheduledFuture.getAndSet(null)?.cancel(false)
            lastStoppedBy.set(actor)
            lastStoppedAt.set(UtcTimestamps.now())
            processLog.info("stop by=$actor (pending schedule cancelled; in-flight load not interrupted)")
            log.info("Entra directory connector stopped by={}", actor)
        }
        return info()
    }

    private fun armSchedule() {
        scheduledFuture.getAndSet(null)?.cancel(false)
        val intervalMs = effectiveRefreshIntervalMs()
        if (intervalMs <= 0L) {
            processLog.info("periodic refresh disabled (refreshIntervalMs<=0)")
            log.info("Entra directory periodic refresh disabled (refresh-interval-ms <= 0)")
            return
        }
        val future = taskScheduler.scheduleWithFixedDelay(
            {
                if (!running.get()) return@scheduleWithFixedDelay
                try {
                    processLog.info("scheduled Graph load starting")
                    val snap = directoryService.refresh(triggeredBy = "SYSTEM-schedule")
                    processLog.info(
                        "scheduled Graph load ok groups=${snap.groupCount} members=${snap.memberCount}"
                    )
                } catch (ex: Exception) {
                    lastError.set(ex.message)
                    processLog.error("scheduled Graph load failed: ${ex.message}")
                    log.error("Scheduled Entra directory refresh failed", ex)
                }
            },
            Duration.ofMillis(intervalMs)
        )
        scheduledFuture.set(future)
        processLog.info("schedule armed fixedDelayMs=$intervalMs")
        log.info(
            "Entra directory schedule armed: fixed delay {} ms (~{} min)",
            intervalMs,
            String.format("%.1f", intervalMs / 60_000.0)
        )
    }

    private fun effectiveRefreshIntervalMs(): Long =
        refreshIntervalOverrideMs.get() ?: properties.refreshIntervalMs

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
