package org.jrtech.platformmanagement.connectors.consumption.eventhub

import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.config.ConsumptionEventHubProperties
import org.jrtech.platformmanagement.exception.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ConsumptionEventHubConnectorTest {

    @Test
    fun `start rejected when disabled`() {
        val connector = ConsumptionEventHubConnector(
            connectorsProperties = ConnectorsProperties(
                consumptionEventHub = ConsumptionEventHubProperties(enabled = false)
            ),
            runtime = InMemoryEventHubProcessorRuntime()
        )
        assertThatThrownBy { connector.start("admin@x.com") }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("disabled")
    }

    @Test
    fun `start and stop toggle running state when enabled`() {
        val runtime = InMemoryEventHubProcessorRuntime()
        val connector = ConsumptionEventHubConnector(
            connectorsProperties = ConnectorsProperties(
                consumptionEventHub = ConsumptionEventHubProperties(enabled = true)
            ),
            runtime = runtime
        )
        assertThat(connector.info().running).isFalse()

        val started = connector.start("admin@x.com")
        assertThat(started.running).isTrue()
        assertThat(started.lastStartedBy).isEqualTo("admin@x.com")
        assertThat(started.logSnapshot.lineCount).isGreaterThan(0)
        assertThat(runtime.isRunning()).isTrue()

        val stopped = connector.stop("admin@x.com")
        assertThat(stopped.running).isFalse()
        assertThat(stopped.lastStoppedBy).isEqualTo("admin@x.com")
        assertThat(runtime.isRunning()).isFalse()
    }

    @Test
    fun `health reports DISABLED when not enabled`() {
        val connector = ConsumptionEventHubConnector(
            connectorsProperties = ConnectorsProperties(),
            runtime = InMemoryEventHubProcessorRuntime()
        )
        assertThat(connector.health().status).isEqualTo("DISABLED")
        assertThat(connector.isEnabled()).isFalse()
    }

    @Test
    fun `configure requireSourceRefId updates public configuration`() {
        val connector = ConsumptionEventHubConnector(
            connectorsProperties = ConnectorsProperties(
                consumptionEventHub = ConsumptionEventHubProperties(
                    enabled = true,
                    requireSourceRefId = true
                )
            ),
            runtime = InMemoryEventHubProcessorRuntime()
        )
        val cfg = connector.configure(mapOf("requireSourceRefId" to false))
        assertThat(cfg["requireSourceRefId"]).isEqualTo(false)
        assertThat(connector.status().requireSourceRefId).isFalse()
    }

    @Test
    fun `autoStartIfConfigured starts when enabled and auto-start true`() {
        val runtime = InMemoryEventHubProcessorRuntime()
        val connector = ConsumptionEventHubConnector(
            connectorsProperties = ConnectorsProperties(
                consumptionEventHub = ConsumptionEventHubProperties(
                    enabled = true,
                    autoStart = true
                )
            ),
            runtime = runtime
        )
        connector.autoStartIfConfigured()
        assertThat(runtime.isRunning()).isTrue()
        assertThat(connector.info().running).isTrue()
        assertThat(connector.info().lastStartedBy).isEqualTo("SYSTEM")
    }

    @Test
    fun `autoStartIfConfigured is no-op when auto-start false`() {
        val runtime = InMemoryEventHubProcessorRuntime()
        val connector = ConsumptionEventHubConnector(
            connectorsProperties = ConnectorsProperties(
                consumptionEventHub = ConsumptionEventHubProperties(
                    enabled = true,
                    autoStart = false
                )
            ),
            runtime = runtime
        )
        connector.autoStartIfConfigured()
        assertThat(runtime.isRunning()).isFalse()
    }
}
