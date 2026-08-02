package org.jrtech.platformmanagement.connectors.consumption.eventhub

import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.runtime.ConnectorConfigSupport
import org.jrtech.platformmanagement.connectors.runtime.ConnectorLogBuffer
import org.jrtech.platformmanagement.connectors.runtime.ManagedConnector
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConnectorInfoResponse
import org.jrtech.platformmanagement.dto.EventHubConnectorStatusResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.logging.logger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Backend process for live consumption Event Hub ingest.
 *
 * Control plane: [ManagedConnector].
 * Data plane: domain rows via `/api/v1/consumptions` (and related APIs).
 */
@Service
class ConsumptionEventHubConnector(
    private val connectorsProperties: ConnectorsProperties,
    private val runtime: EventHubProcessorRuntime
) : ManagedConnector {

    private val log = logger()
    private val processLog = ConnectorLogBuffer()
    private val lastStartedBy = AtomicReference<String?>(null)
    private val lastStoppedBy = AtomicReference<String?>(null)
    private val lastStartedAt = AtomicReference<Instant?>(null)
    private val lastStoppedAt = AtomicReference<Instant?>(null)
    private val lastError = AtomicReference<String?>(null)
    private val requireSourceRefIdOverride = AtomicReference<Boolean?>(null)

    override val id: ConnectorId = ConnectorId.CONSUMPTION_EVENT_HUB

    override fun isEnabled(): Boolean = connectorsProperties.consumptionEventHub.enabled

    @EventListener(ApplicationReadyEvent::class)
    fun autoStartIfConfigured() {
        val props = connectorsProperties.consumptionEventHub
        if (props.enabled && props.autoStart) {
            try {
                start(actor = "SYSTEM")
                processLog.info("auto-start completed")
                log.info(
                    "Event Hub connector auto-started " +
                        "(app.connectors.consumption-eventhub.auto-start=true)"
                )
            } catch (ex: Exception) {
                lastError.set(ex.message)
                processLog.error("auto-start failed: ${ex.message}")
                log.error("Event Hub auto-start failed: {}", ex.message, ex)
            }
        }
    }

    override fun info(): ConnectorInfoResponse {
        val props = connectorsProperties.consumptionEventHub
        val isRunning = runtime.isRunning()
        val statusLabel = when {
            !props.enabled -> "DISABLED"
            isRunning -> "RUNNING"
            lastError.get() != null -> "DEGRADED"
            else -> "STOPPED"
        }
        val detail = when {
            !props.enabled -> "disabled"
            isRunning -> "running"
            lastError.get() != null -> "last-error"
            else -> "stopped"
        }
        return ConnectorInfoResponse(
            id = id.pathId,
            enabled = props.enabled,
            configured = props.isConfigured(),
            running = isRunning,
            status = statusLabel,
            detail = detail,
            lastStartedBy = lastStartedBy.get(),
            lastStartedAt = lastStartedAt.get(),
            lastStoppedBy = lastStoppedBy.get(),
            lastStoppedAt = lastStoppedAt.get(),
            lastError = lastError.get(),
            attributes = buildMap {
                put("runtime", runtime.describe())
                put("dataPlane", "/api/v1/consumptions")
                props.eventHubName.takeIf { it.isNotBlank() }?.let { put("eventHubName", it) }
            },
            configuration = configuration(),
            logSnapshot = processLog.snapshot()
        )
    }

    fun status(): EventHubConnectorStatusResponse {
        val props = connectorsProperties.consumptionEventHub
        val i = info()
        return EventHubConnectorStatusResponse(
            id = i.id,
            enabled = i.enabled,
            configured = i.configured,
            running = i.running,
            autoStart = props.autoStart,
            fullyQualifiedNamespace = props.fullyQualifiedNamespace.ifBlank { null },
            eventHubName = props.eventHubName.ifBlank { null },
            consumerGroup = props.consumerGroup,
            requireSourceRefId = effectiveRequireSourceRefId(),
            runtime = runtime.describe(),
            lastStartedBy = i.lastStartedBy,
            lastStartedAt = i.lastStartedAt,
            lastStoppedBy = i.lastStoppedBy,
            lastStoppedAt = i.lastStoppedAt,
            lastError = i.lastError
        )
    }

    override fun configuration(): Map<String, Any?> {
        val props = connectorsProperties.consumptionEventHub
        return linkedMapOf(
            "enabled" to props.enabled,
            "autoStart" to props.autoStart,
            "fullyQualifiedNamespace" to props.fullyQualifiedNamespace.ifBlank { null },
            "eventHubName" to props.eventHubName.ifBlank { null },
            "consumerGroup" to props.consumerGroup,
            "checkpointContainer" to props.checkpointContainer,
            "checkpointStorageConfigured" to props.checkpointStorageAccountUrl.isNotBlank(),
            "requireSourceRefId" to effectiveRequireSourceRefId(),
            "poisonSkipAfter" to props.poisonSkipAfter,
            "dataPlane" to listOf("/api/v1/consumptions")
        )
    }

    override fun configure(updates: Map<String, Any?>): Map<String, Any?> {
        ConnectorConfigSupport.requireKnownKeys(updates, setOf("requireSourceRefId"))
        ConnectorConfigSupport.optionalBoolean(updates, "requireSourceRefId")?.let { v ->
            requireSourceRefIdOverride.set(v)
            processLog.info("configuration requireSourceRefId=$v")
        }
        return configuration()
    }

    override fun start(actor: String): ConnectorInfoResponse {
        val props = connectorsProperties.consumptionEventHub
        if (!props.enabled) {
            throw BadRequestException(
                "Event Hub connector is disabled " +
                    "(set app.connectors.consumption-eventhub.enabled=true)"
            )
        }
        if (!props.isConfigured() && runtime is InMemoryEventHubProcessorRuntime) {
            processLog.warn("starting without full Azure config runtime=${runtime.describe()}")
            log.warn(
                "Starting Event Hub connector without full Azure config (runtime={})",
                runtime.describe()
            )
        }
        try {
            runtime.start()
            lastStartedBy.set(actor)
            lastStartedAt.set(UtcTimestamps.now())
            lastError.set(null)
            processLog.info("start by=$actor runtime=${runtime.describe()}")
            log.info("Event Hub connector started by={}", actor)
        } catch (ex: Exception) {
            lastError.set(ex.message)
            processLog.error("start failed by=$actor: ${ex.message}")
            throw if (ex is BadRequestException) ex
            else BadRequestException("Failed to start Event Hub connector: ${ex.message}")
        }
        return info()
    }

    override fun stop(actor: String): ConnectorInfoResponse {
        try {
            runtime.stop()
            lastStoppedBy.set(actor)
            lastStoppedAt.set(UtcTimestamps.now())
            lastError.set(null)
            processLog.info("stop by=$actor")
            log.info("Event Hub connector stopped by={}", actor)
        } catch (ex: Exception) {
            lastError.set(ex.message)
            processLog.error("stop failed by=$actor: ${ex.message}")
            throw if (ex is BadRequestException) ex
            else BadRequestException("Failed to stop Event Hub connector: ${ex.message}")
        }
        return info()
    }

    private fun effectiveRequireSourceRefId(): Boolean =
        requireSourceRefIdOverride.get()
            ?: connectorsProperties.consumptionEventHub.requireSourceRefId

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
