package org.jrtech.platformmanagement.connectors.entra

import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.entra.EntraDirectoryMember
import org.jrtech.platformmanagement.entra.EntraDirectorySnapshot
import org.jrtech.platformmanagement.entra.EntraGroup
import org.jrtech.platformmanagement.entra.EntraGroupDirectoryService
import org.jrtech.platformmanagement.entra.EntraGroupWithMembers
import org.jrtech.platformmanagement.exception.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class EntraDirectoryConnectorTest {

    private val directoryService = mock<EntraGroupDirectoryService>()

    @Test
    fun `status reports disabled when not enabled`() {
        val connector = EntraDirectoryConnector(
            properties = EntraDirectoryProperties(enabled = false),
            directoryService = directoryService
        )
        whenever(directoryService.snapshot()).thenReturn(
            EntraDirectorySnapshot(
                enabled = false,
                groupNamePrefix = "Platform-System-",
                loadedAt = null,
                groups = emptyList()
            )
        )
        whenever(directoryService.hasGraphClient()).thenReturn(false)
        whenever(directoryService.isRefreshInProgress()).thenReturn(false)
        whenever(directoryService.lastLoadedAt()).thenReturn(null)
        whenever(directoryService.lastRefreshStartedAt()).thenReturn(null)
        whenever(directoryService.lastRefreshFinishedAt()).thenReturn(null)
        whenever(directoryService.lastRefreshBy()).thenReturn(null)
        whenever(directoryService.lastError()).thenReturn(null)
        whenever(directoryService.allMembers()).thenReturn(emptyList())

        val status = connector.status()
        assertThat(status.id).isEqualTo("entra-directory")
        assertThat(status.enabled).isFalse()
        assertThat(status.detail).isEqualTo("disabled")
        assertThat(connector.health().status).isEqualTo("DISABLED")
    }

    @Test
    fun `status reports ready with counts after load`() {
        val loadedAt = Instant.parse("2024-07-01T10:00:00Z")
        val connector = EntraDirectoryConnector(
            properties = EntraDirectoryProperties(enabled = true, refreshIntervalMs = 900_000L),
            directoryService = directoryService
        )
        whenever(directoryService.snapshot()).thenReturn(
            EntraDirectorySnapshot(
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
        )
        whenever(directoryService.hasGraphClient()).thenReturn(true)
        whenever(directoryService.isRefreshInProgress()).thenReturn(false)
        whenever(directoryService.lastLoadedAt()).thenReturn(loadedAt)
        whenever(directoryService.lastRefreshStartedAt()).thenReturn(loadedAt)
        whenever(directoryService.lastRefreshFinishedAt()).thenReturn(loadedAt)
        whenever(directoryService.lastRefreshBy()).thenReturn("SYSTEM-schedule")
        whenever(directoryService.lastError()).thenReturn(null)
        whenever(directoryService.allMembers()).thenReturn(
            listOf(EntraDirectoryMember(id = "u1", displayName = "Alice"))
        )

        val status = connector.status()
        assertThat(status.configured).isTrue()
        assertThat(status.groupCount).isEqualTo(1)
        assertThat(status.memberCount).isEqualTo(1)
        assertThat(status.uniqueMemberCount).isEqualTo(1)
        assertThat(status.lastRefreshBy).isEqualTo("SYSTEM-schedule")
        assertThat(status.detail).isEqualTo("ready")
        assertThat(connector.health().status).isEqualTo("UP")
        assertThat(connector.health().attributes["groupCount"]).isEqualTo("1")
    }

    @Test
    fun `start rejected when disabled`() {
        val connector = EntraDirectoryConnector(
            properties = EntraDirectoryProperties(enabled = false),
            directoryService = directoryService
        )
        assertThatThrownBy { connector.start("admin@x.com") }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("disabled")
    }

    @Test
    fun `start triggers refresh with actor`() {
        val connector = EntraDirectoryConnector(
            properties = EntraDirectoryProperties(enabled = true),
            directoryService = directoryService
        )
        whenever(directoryService.refresh(triggeredBy = "admin@x.com")).thenReturn(
            EntraDirectorySnapshot(
                enabled = true,
                groupNamePrefix = "Platform-System-",
                loadedAt = Instant.parse("2024-07-01T11:00:00Z"),
                groups = emptyList()
            )
        )
        whenever(directoryService.snapshot()).thenReturn(
            EntraDirectorySnapshot(
                enabled = true,
                groupNamePrefix = "Platform-System-",
                loadedAt = Instant.parse("2024-07-01T11:00:00Z"),
                groups = emptyList()
            )
        )
        whenever(directoryService.hasGraphClient()).thenReturn(true)
        whenever(directoryService.isRefreshInProgress()).thenReturn(false)
        whenever(directoryService.lastLoadedAt()).thenReturn(Instant.parse("2024-07-01T11:00:00Z"))
        whenever(directoryService.lastRefreshStartedAt()).thenReturn(Instant.parse("2024-07-01T11:00:00Z"))
        whenever(directoryService.lastRefreshFinishedAt()).thenReturn(Instant.parse("2024-07-01T11:00:00Z"))
        whenever(directoryService.lastRefreshBy()).thenReturn("admin@x.com")
        whenever(directoryService.lastError()).thenReturn(null)
        whenever(directoryService.allMembers()).thenReturn(emptyList())

        val status = connector.start("admin@x.com")
        assertThat(status.lastRefreshBy).isEqualTo("admin@x.com")
        verify(directoryService).refresh(triggeredBy = "admin@x.com")
    }
}
