package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.dto.EntraDirectorySnapshotResponse
import org.jrtech.platformmanagement.dto.EntraGroupMemberResponse
import org.jrtech.platformmanagement.dto.EntraGroupWithMembersResponse
import org.jrtech.platformmanagement.entra.EntraDirectoryMember
import org.jrtech.platformmanagement.entra.EntraDirectorySnapshot
import org.jrtech.platformmanagement.entra.EntraGroup
import org.jrtech.platformmanagement.entra.EntraGroupDirectoryService
import org.jrtech.platformmanagement.entra.EntraGroupWithMembers
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.service.AuditPrincipalResolver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class EntraDirectoryControllerTest {

    private val directoryService = mock<EntraGroupDirectoryService>()
    private val auditPrincipalResolver = mock<AuditPrincipalResolver>()
    private val controller = EntraDirectoryController(directoryService, auditPrincipalResolver)

    @Test
    fun `listGroups returns snapshot response`() {
        val response = EntraDirectorySnapshotResponse(
            enabled = true,
            groupNamePrefix = "Platform-System-",
            loadedAt = Instant.parse("2024-06-01T00:00:00Z"),
            groupCount = 1,
            memberCount = 1,
            groups = listOf(
                EntraGroupWithMembersResponse(
                    id = "g1",
                    displayName = "Platform-System-Reader",
                    description = null,
                    members = listOf(
                        EntraGroupMemberResponse(
                            id = "u1",
                            displayName = "Alice",
                            userPrincipalName = "alice@contoso.com",
                            mail = null,
                            odataType = "#microsoft.graph.user"
                        )
                    )
                )
            )
        )
        whenever(directoryService.snapshotResponse()).thenReturn(response)

        assertThat(controller.listGroups()).isEqualTo(response)
    }

    @Test
    fun `listMembers maps unique directory members`() {
        whenever(directoryService.allMembers()).thenReturn(
            listOf(
                EntraDirectoryMember(
                    id = "u1",
                    displayName = "Alice",
                    userPrincipalName = "alice@contoso.com",
                    mail = "alice@contoso.com",
                    odataType = "#microsoft.graph.user"
                )
            )
        )

        val members = controller.listMembers()
        assertThat(members).containsExactly(
            EntraGroupMemberResponse(
                id = "u1",
                displayName = "Alice",
                userPrincipalName = "alice@contoso.com",
                mail = "alice@contoso.com",
                odataType = "#microsoft.graph.user"
            )
        )
    }

    @Test
    fun `refresh returns updated snapshot`() {
        val snap = EntraDirectorySnapshot(
            enabled = true,
            groupNamePrefix = "Platform-System-",
            loadedAt = Instant.parse("2024-06-01T12:00:00Z"),
            groups = listOf(
                EntraGroupWithMembers(
                    group = EntraGroup("g1", "Platform-System-Maintainer"),
                    members = emptyList()
                )
            )
        )
        whenever(auditPrincipalResolver.current()).thenReturn("maintainer@x.com")
        whenever(directoryService.refresh(triggeredBy = "maintainer@x.com")).thenReturn(snap)
        whenever(directoryService.snapshotResponse()).thenReturn(
            EntraDirectorySnapshotResponse(
                enabled = true,
                groupNamePrefix = "Platform-System-",
                loadedAt = snap.loadedAt,
                groupCount = 1,
                memberCount = 0,
                groups = listOf(
                    EntraGroupWithMembersResponse(
                        id = "g1",
                        displayName = "Platform-System-Maintainer",
                        description = null,
                        members = emptyList()
                    )
                )
            )
        )

        val result = controller.refresh()
        assertThat(result.groupCount).isEqualTo(1)
        assertThat(result.groups[0].displayName).isEqualTo("Platform-System-Maintainer")
        verify(directoryService).refresh(triggeredBy = "maintainer@x.com")
    }

    @Test
    fun `refresh wraps Graph failures as BadRequestException`() {
        whenever(auditPrincipalResolver.current()).thenReturn("admin@x.com")
        whenever(directoryService.refresh(triggeredBy = "admin@x.com"))
            .thenThrow(IllegalStateException("Graph 401"))

        assertThatThrownBy { controller.refresh() }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("Graph 401")
    }
}
