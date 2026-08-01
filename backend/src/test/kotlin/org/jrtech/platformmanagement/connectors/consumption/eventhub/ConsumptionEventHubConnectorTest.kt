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
        assertThat(connector.status().running).isFalse()

        val started = connector.start("admin@x.com")
        assertThat(started.running).isTrue()
        assertThat(started.lastStartedBy).isEqualTo("admin@x.com")
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
}
