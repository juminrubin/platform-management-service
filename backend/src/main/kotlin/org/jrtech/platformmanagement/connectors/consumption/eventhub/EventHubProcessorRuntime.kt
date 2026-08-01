package org.jrtech.platformmanagement.connectors.consumption.eventhub

/**
 * Lifecycle for the continuous Event Hub consumer.
 * Implementations may be Azure-backed or in-memory (tests / misconfigured).
 */
interface EventHubProcessorRuntime {
    fun start()
    fun stop()
    fun isRunning(): Boolean
    /** Human-readable configuration summary (no secrets). */
    fun describe(): String
}
