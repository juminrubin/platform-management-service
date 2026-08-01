package org.jrtech.platformmanagement.connectors

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.junit.jupiter.api.Test

class ConnectorIdTest {

    @Test
    fun `fromPathId resolves public path ids`() {
        assertThat(ConnectorId.fromPathId("consumption-storage"))
            .isEqualTo(ConnectorId.CONSUMPTION_BLOB_AVRO)
        assertThat(ConnectorId.fromPathId("consumption-eventhub"))
            .isEqualTo(ConnectorId.CONSUMPTION_EVENT_HUB)
        assertThat(ConnectorId.fromPathId("entra-directory"))
            .isEqualTo(ConnectorId.ENTRA_DIRECTORY)
    }

    @Test
    fun `fromPathId accepts enum names`() {
        assertThat(ConnectorId.fromPathId("CONSUMPTION_EVENT_HUB"))
            .isEqualTo(ConnectorId.CONSUMPTION_EVENT_HUB)
    }

    @Test
    fun `requirePathId rejects unknown`() {
        assertThatThrownBy { ConnectorId.requirePathId("unknown") }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
