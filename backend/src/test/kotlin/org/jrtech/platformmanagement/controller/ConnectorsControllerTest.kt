package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobContainerConnector
import org.jrtech.platformmanagement.connectors.consumption.eventhub.ConsumptionEventHubConnector
import org.jrtech.platformmanagement.connectors.entra.EntraDirectoryConnector
import org.jrtech.platformmanagement.connectors.runtime.ManagedConnector
import org.jrtech.platformmanagement.dto.ConnectorInfoResponse
import org.jrtech.platformmanagement.dto.ConnectorLogSnapshotResponse
import org.jrtech.platformmanagement.dto.ConnectorConfigureRequest
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.service.AuditPrincipalResolver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConnectorsControllerTest {

    private val eventHub = mock<ConsumptionEventHubConnector>()
    private val blob = mock<ConsumptionBlobContainerConnector>()
    private val entra = mock<EntraDirectoryConnector>()
    private val audit = mock<AuditPrincipalResolver>()

    private val controller = ConnectorsController(
        managedConnectors = listOf(eventHub, blob, entra),
        eventHubConnector = eventHub,
        blobConnector = blob,
        entraDirectoryConnector = entra,
        auditPrincipalResolver = audit
    )

    init {
        whenever(eventHub.id).thenReturn(ConnectorId.CONSUMPTION_EVENT_HUB)
        whenever(blob.id).thenReturn(ConnectorId.CONSUMPTION_BLOB_AVRO)
        whenever(entra.id).thenReturn(ConnectorId.ENTRA_DIRECTORY)
    }

    @Test
    fun `listConnectors maps summaries without full log bodies`() {
        whenever(eventHub.info()).thenReturn(sampleInfo("consumption-eventhub", running = false))
        whenever(blob.info()).thenReturn(sampleInfo("consumption-storage", running = false))
        whenever(entra.info()).thenReturn(sampleInfo("entra-directory", running = true))

        val list = controller.listConnectors()
        assertThat(list.connectors).hasSize(3)
        assertThat(list.connectors.map { it.id }).containsExactly(
            "consumption-eventhub",
            "consumption-storage",
            "entra-directory"
        )
        assertThat(list.connectors[2].running).isTrue()
        assertThat(list.connectors[2].status).isEqualTo("RUNNING")
    }

    @Test
    fun `getConnector returns full info for each id`() {
        val info = sampleInfo("entra-directory", running = true)
        whenever(entra.info()).thenReturn(info)
        assertThat(controller.getConnector("entra-directory")).isEqualTo(info)
        verify(entra).info()
    }

    @Test
    fun `get and put config delegate to connector`() {
        whenever(blob.configuration()).thenReturn(mapOf("startDate" to "2024-01-01"))
        whenever(blob.configure(mapOf("endDate" to "2024-01-02")))
            .thenReturn(mapOf("startDate" to "2024-01-01", "endDate" to "2024-01-02"))

        val got = controller.getConfig("consumption-storage")
        assertThat(got.id).isEqualTo("consumption-storage")
        assertThat(got.configuration["startDate"]).isEqualTo("2024-01-01")

        val updated = controller.putConfig(
            "consumption-storage",
            ConnectorConfigureRequest(configuration = mapOf("endDate" to "2024-01-02"))
        )
        assertThat(updated.configuration["endDate"]).isEqualTo("2024-01-02")
        verify(blob).configure(mapOf("endDate" to "2024-01-02"))
    }

    @Test
    fun `start and stop use audit principal`() {
        whenever(audit.current()).thenReturn("admin@x.com")
        val started = sampleInfo("consumption-eventhub", running = true)
        val stopped = sampleInfo("consumption-eventhub", running = false)
        whenever(eventHub.start("admin@x.com")).thenReturn(started)
        whenever(eventHub.stop("admin@x.com")).thenReturn(stopped)

        assertThat(controller.startConnector("consumption-eventhub").running).isTrue()
        assertThat(controller.stopConnector("CONSUMPTION_EVENT_HUB").running).isFalse()
        verify(eventHub).start("admin@x.com")
        verify(eventHub).stop("admin@x.com")
    }

    @Test
    fun `unknown connector id is not found`() {
        assertThatThrownBy { controller.getConnector("no-such") }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    private fun sampleInfo(id: String, running: Boolean) = ConnectorInfoResponse(
        id = id,
        enabled = true,
        configured = true,
        running = running,
        status = if (running) "RUNNING" else "STOPPED",
        detail = if (running) "running" else "stopped",
        attributes = mapOf("dataPlane" to "/api/v1/x"),
        configuration = mapOf("enabled" to true),
        logSnapshot = ConnectorLogSnapshotResponse(
            maxBytes = 32 * 1024,
            bytes = 10,
            lineCount = 1,
            lines = listOf("log line")
        )
    )
}
