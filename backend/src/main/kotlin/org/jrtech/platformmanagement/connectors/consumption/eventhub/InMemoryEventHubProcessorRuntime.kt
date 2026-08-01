package org.jrtech.platformmanagement.connectors.consumption.eventhub

import org.jrtech.platformmanagement.logging.logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-local runtime used when Azure Event Hubs is not fully configured,
 * or for unit tests. Tracks start/stop without network I/O.
 */
class InMemoryEventHubProcessorRuntime(
    private val label: String = "in-memory"
) : EventHubProcessorRuntime {

    private val log = logger()
    private val running = AtomicBoolean(false)

    override fun start() {
        if (running.compareAndSet(false, true)) {
            log.info("Event Hub processor started ({})", label)
        }
    }

    override fun stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Event Hub processor stopped ({})", label)
        }
    }

    override fun isRunning(): Boolean = running.get()

    override fun describe(): String = "mode=$label running=${running.get()}"
}
