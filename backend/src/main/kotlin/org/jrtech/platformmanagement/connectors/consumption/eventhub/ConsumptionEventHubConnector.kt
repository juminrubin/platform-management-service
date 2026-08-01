package org.jrtech.platformmanagement.connectors.consumption.eventhub

import org.jrtech.platformmanagement.connectors.ConnectorHealthContributor
import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.EventHubConnectorStatusResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.logging.logger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Lifecycle facade for the consumption Event Hub connector.
 * Start/stop are intended for [org.jrtech.platformmanagement.security.AppRoles.SYSTEM_MAINTAINER]
 * via the Web API.
 */
@Service
class ConsumptionEventHubConnector(
    private val connectorsProperties: ConnectorsProperties,
    private val runtime: EventHubProcessorRuntime
) : ConnectorHealthContributor {

    private val log = logger()
    private val lastStartedBy = AtomicReference<String?>(null)
    private val lastStoppedBy = AtomicReference<String?>(null)
    private val lastStartedAt = AtomicReference<Instant?>(null)
    private val lastStoppedAt = AtomicReference<Instant?>(null)
    private val lastError = AtomicReference<String?>(null)

    override val id: ConnectorId = ConnectorId.CONSUMPTION_EVENT_HUB

    override fun isEnabled(): Boolean = connectorsProperties.consumptionEventHub.enabled

    /**
     * Optional auto-start when enabled and auto-start=true.
     * Primary control remains Maintainer Web API start/stop.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun autoStartIfConfigured() {
        val props = connectorsProperties.consumptionEventHub
        if (props.enabled && props.autoStart) {
            try {
                start(actor = "SYSTEM")
                log.info(
                    "Event Hub connector auto-started " +
                        "(app.connectors.consumption-eventhub.auto-start=true)"
                )
            } catch (ex: Exception) {
                log.error("Event Hub auto-start failed: {}", ex.message, ex)
            }
        }
    }

    fun status(): EventHubConnectorStatusResponse {
        val props = connectorsProperties.consumptionEventHub
        val running = runtime.isRunning()
        return EventHubConnectorStatusResponse(
            id = id.pathId,
            enabled = props.enabled,
            configured = props.isConfigured(),
            running = running,
            autoStart = props.autoStart,
            fullyQualifiedNamespace = props.fullyQualifiedNamespace.ifBlank { null },
            eventHubName = props.eventHubName.ifBlank { null },
            consumerGroup = props.consumerGroup,
            requireSourceRefId = props.requireSourceRefId,
            runtime = runtime.describe(),
            lastStartedBy = lastStartedBy.get(),
            lastStartedAt = lastStartedAt.get(),
            lastStoppedBy = lastStoppedBy.get(),
            lastStoppedAt = lastStoppedAt.get(),
            lastError = lastError.get()
        )
    }

    /**
     * Start the processor. Idempotent if already running.
     * @param actor audit principal (from JWT resolver or SYSTEM for auto-start)
     */
    fun start(actor: String): EventHubConnectorStatusResponse {
        val props = connectorsProperties.consumptionEventHub
        if (!props.enabled) {
            throw BadRequestException(
                "Event Hub connector is disabled " +
                    "(set app.connectors.consumption-eventhub.enabled=true)"
            )
        }
        if (!props.isConfigured() && runtime is InMemoryEventHubProcessorRuntime) {
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
            log.info("Event Hub connector started by={}", actor)
        } catch (ex: Exception) {
            lastError.set(ex.message)
            throw if (ex is BadRequestException) ex
            else BadRequestException("Failed to start Event Hub connector: ${ex.message}")
        }
        return status()
    }

    /**
     * Stop the processor. Idempotent if already stopped.
     */
    fun stop(actor: String): EventHubConnectorStatusResponse {
        try {
            runtime.stop()
            lastStoppedBy.set(actor)
            lastStoppedAt.set(UtcTimestamps.now())
            lastError.set(null)
            log.info("Event Hub connector stopped by={}", actor)
        } catch (ex: Exception) {
            lastError.set(ex.message)
            throw if (ex is BadRequestException) ex
            else BadRequestException("Failed to stop Event Hub connector: ${ex.message}")
        }
        return status()
    }

    override fun health(): ConnectorHealthView {
        val s = status()
        val statusLabel = when {
            !s.enabled -> "DISABLED"
            s.running -> "RUNNING"
            else -> "STOPPED"
        }
        return ConnectorHealthView(
            id = id,
            enabled = s.enabled,
            status = statusLabel,
            detail = s.runtime,
            attributes = buildMap {
                put("configured", s.configured.toString())
                put("running", s.running.toString())
                s.eventHubName?.let { put("eventHubName", it) }
                s.lastError?.let { put("lastError", it) }
            }
        )
    }
}
