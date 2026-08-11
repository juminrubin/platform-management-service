package org.jrtech.platformmanagement.connectors.entra

import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.config.azure.AzureCredentialProperties
import org.jrtech.platformmanagement.entra.EntraDirectoryMember
import org.jrtech.platformmanagement.entra.EntraDirectorySnapshot
import org.jrtech.platformmanagement.entra.EntraGroup
import org.jrtech.platformmanagement.entra.EntraGroupDirectoryService
import org.jrtech.platformmanagement.entra.EntraGroupWithMembers
import org.jrtech.platformmanagement.exception.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

class EntraDirectoryConnectorTest {

    private val directoryService = mock<EntraGroupDirectoryService>()
    private val taskScheduler = mock<TaskScheduler>()
    private val scheduledFuture = mock<ScheduledFuture<*>>()

    init {
        whenever(
            taskScheduler.scheduleWithFixedDelay(any(), any<Duration>())
        ).thenReturn(scheduledFuture)
    }

    @Test
    fun `info reports disabled when not enabled`() {
        val connector = connector(EntraDirectoryProperties(enabled = false))
        stubStatusReads(
            snapshot = emptySnapshot(enabled = false),
            hasGraph = false
        )

        val info = connector.info()
        assertThat(info.id).isEqualTo("entra-directory")
        assertThat(info.enabled).isFalse()
        assertThat(info.running).isFalse()
        assertThat(info.detail).isEqualTo("disabled")
        assertThat(info.status).isEqualTo("DISABLED")
        assertThat(info.logSnapshot.maxBytes).isEqualTo(32 * 1024)
        assertThat(info.configuration["dataPlane"]).isNotNull
        assertThat(connector.health().status).isEqualTo("DISABLED")
    }

    @Test
    fun `start arms schedule and records log lines`() {
        val loadedAt = Instant.parse("2024-07-01T10:00:00Z")
        val connector = connector(
            EntraDirectoryProperties(enabled = true, refreshIntervalMs = 900_000L)
        )
        whenever(directoryService.refresh(triggeredBy = "admin@x.com")).thenReturn(
            snapshotWithOneGroup(loadedAt)
        )
        stubStatusReads(
            snapshot = snapshotWithOneGroup(loadedAt),
            hasGraph = true,
            lastLoadedAt = loadedAt,
            lastRefreshBy = "admin@x.com",
            uniqueMembers = listOf(EntraDirectoryMember(id = "u1", displayName = "Alice"))
        )

        val info = connector.start("admin@x.com")
        assertThat(info.running).isTrue()
        assertThat(info.status).isEqualTo("RUNNING")
        assertThat(info.logSnapshot.lineCount).isGreaterThan(0)
        assertThat(info.logSnapshot.lines.any { it.contains("start by=admin@x.com") }).isTrue()
        verify(taskScheduler).scheduleWithFixedDelay(any(), any<Duration>())
    }

    @Test
    fun `start rejected when disabled`() {
        val connector = connector(EntraDirectoryProperties(enabled = false))
        assertThatThrownBy { connector.start("admin@x.com") }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("disabled")
    }

    @Test
    fun `start is idempotent when already running`() {
        val connector = connector(EntraDirectoryProperties(enabled = true))
        whenever(directoryService.refresh(triggeredBy = "admin@x.com")).thenReturn(
            emptySnapshot(enabled = true, loadedAt = Instant.parse("2024-07-01T11:00:00Z"))
        )
        stubStatusReads(
            snapshot = emptySnapshot(
                enabled = true,
                loadedAt = Instant.parse("2024-07-01T11:00:00Z")
            ),
            hasGraph = true,
            lastLoadedAt = Instant.parse("2024-07-01T11:00:00Z"),
            lastRefreshBy = "admin@x.com"
        )

        connector.start("admin@x.com")
        connector.start("admin@x.com")

        verify(directoryService, times(1)).refresh(triggeredBy = "admin@x.com")
        verify(taskScheduler, times(1)).scheduleWithFixedDelay(any(), any<Duration>())
    }

    @Test
    fun `stop disarms schedule and retains data-plane attributes`() {
        val loadedAt = Instant.parse("2024-07-01T10:00:00Z")
        val connector = connector(EntraDirectoryProperties(enabled = true))
        whenever(directoryService.refresh(triggeredBy = "admin@x.com")).thenReturn(
            snapshotWithOneGroup(loadedAt)
        )
        stubStatusReads(
            snapshot = snapshotWithOneGroup(loadedAt),
            hasGraph = true,
            lastLoadedAt = loadedAt,
            lastRefreshBy = "admin@x.com",
            uniqueMembers = listOf(EntraDirectoryMember(id = "u1", displayName = "Alice"))
        )

        connector.start("admin@x.com")
        val stopped = connector.stop("admin@x.com")

        assertThat(stopped.running).isFalse()
        assertThat(stopped.lastStoppedBy).isEqualTo("admin@x.com")
        assertThat(stopped.attributes["groupCount"]).isEqualTo("1")
        assertThat(stopped.attributes["dataPlane"]).contains("/api/v1/entra/groups")
        assertThat(stopped.detail).isEqualTo("stopped")
        assertThat(connector.health().status).isEqualTo("STOPPED")
        verify(scheduledFuture).cancel(false)
    }

    @Test
    fun `configure refreshIntervalMs updates configuration`() {
        val connector = connector(
            EntraDirectoryProperties(enabled = true, refreshIntervalMs = 900_000L)
        )
        stubStatusReads(snapshot = emptySnapshot(enabled = true), hasGraph = true)
        val cfg = connector.configure(mapOf("refreshIntervalMs" to 60_000L))
        assertThat(cfg["refreshIntervalMs"]).isEqualTo(60_000L)
    }

    @Test
    fun `autoStartIfConfigured starts when enabled and auto-start true`() {
        val connector = connector(
            EntraDirectoryProperties(enabled = true, autoStart = true)
        )
        whenever(directoryService.refresh(triggeredBy = "SYSTEM")).thenReturn(
            emptySnapshot(enabled = true, loadedAt = Instant.parse("2024-07-01T11:00:00Z"))
        )
        stubStatusReads(
            snapshot = emptySnapshot(
                enabled = true,
                loadedAt = Instant.parse("2024-07-01T11:00:00Z")
            ),
            hasGraph = true,
            lastLoadedAt = Instant.parse("2024-07-01T11:00:00Z"),
            lastRefreshBy = "SYSTEM"
        )

        connector.autoStartIfConfigured()
        verify(directoryService).refresh(triggeredBy = "SYSTEM")
        assertThat(connector.info().running).isTrue()
    }

    @Test
    fun `autoStartIfConfigured is no-op when auto-start false`() {
        val connector = connector(
            EntraDirectoryProperties(enabled = true, autoStart = false)
        )
        stubStatusReads(
            snapshot = emptySnapshot(enabled = true),
            hasGraph = true
        )
        connector.autoStartIfConfigured()
        verify(directoryService, never()).refresh(triggeredBy = any())
        assertThat(connector.info().running).isFalse()
    }

    private fun connector(properties: EntraDirectoryProperties) =
        EntraDirectoryConnector(
            properties = properties,
            azureCredential = AzureCredentialProperties(),
            directoryService = directoryService,
            taskScheduler = taskScheduler
        )

    private fun stubStatusReads(
        snapshot: EntraDirectorySnapshot,
        hasGraph: Boolean,
        lastLoadedAt: Instant? = null,
        lastRefreshBy: String? = null,
        uniqueMembers: List<EntraDirectoryMember> = emptyList()
    ) {
        whenever(directoryService.snapshot()).thenReturn(snapshot)
        whenever(directoryService.hasGraphClient()).thenReturn(hasGraph)
        whenever(directoryService.isRefreshInProgress()).thenReturn(false)
        whenever(directoryService.lastLoadedAt()).thenReturn(lastLoadedAt)
        whenever(directoryService.lastRefreshStartedAt()).thenReturn(lastLoadedAt)
        whenever(directoryService.lastRefreshFinishedAt()).thenReturn(lastLoadedAt)
        whenever(directoryService.lastRefreshBy()).thenReturn(lastRefreshBy)
        whenever(directoryService.lastError()).thenReturn(null)
        whenever(directoryService.allMembers()).thenReturn(uniqueMembers)
    }

    private fun emptySnapshot(
        enabled: Boolean,
        loadedAt: Instant? = null
    ) = EntraDirectorySnapshot(
        enabled = enabled,
        groupNamePrefix = "Platform-System-",
        loadedAt = loadedAt,
        groups = emptyList()
    )

    private fun snapshotWithOneGroup(loadedAt: Instant) = EntraDirectorySnapshot(
        enabled = true,
        groupNamePrefix = "Platform-System-",
        loadedAt = loadedAt,
        groups = listOf(
            EntraGroupWithMembers(
                group = EntraGroup("g1", "Platform-System-Maintainer"),
                members = listOf(
                    EntraDirectoryMember(id = "u1", displayName = "Alice")
                )
            )
        )
    )
}
