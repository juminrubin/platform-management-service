package org.jrtech.platformmanagement.entra

import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

class EntraGroupDirectoryServiceTest {

    @Test
    fun `refresh is no-op when directory loading is disabled`() {
        val graph = mock<MicrosoftGraphClient>()
        val service = service(
            properties = EntraDirectoryProperties(enabled = false, groupNamePrefix = "Platform-System-"),
            graph = graph
        )

        val snap = service.refresh()

        assertThat(snap.enabled).isFalse()
        assertThat(snap.groups).isEmpty()
        assertThat(snap.loadedAt).isNull()
        verify(graph, never()).listGroupsByDisplayNamePrefix(any())
    }

    @Test
    fun `refresh loads Platform-System groups and members into ConcurrentHashMap`() {
        val graph = mock<MicrosoftGraphClient>()
        whenever(graph.listGroupsByDisplayNamePrefix("Platform-System-")).thenReturn(
            listOf(
                EntraGroup("g-maint", "Platform-System-Maintainer", "Admins"),
                EntraGroup("g-read", "Platform-System-Reader", null),
                EntraGroup("g-other", "Other-Group", null) // filtered out by startsWith after list
            )
        )
        whenever(graph.listGroupMembers("g-maint", false)).thenReturn(
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
        whenever(graph.listGroupMembers("g-read", false)).thenReturn(
            listOf(
                EntraDirectoryMember(
                    id = "u1",
                    displayName = "Alice",
                    userPrincipalName = "alice@contoso.com",
                    odataType = "#microsoft.graph.user"
                ),
                EntraDirectoryMember(
                    id = "u2",
                    displayName = "Bob",
                    userPrincipalName = "bob@contoso.com",
                    odataType = "#microsoft.graph.user"
                )
            )
        )

        val service = service(
            properties = EntraDirectoryProperties(
                enabled = true,
                groupNamePrefix = "Platform-System-",
                includeTransitiveMembers = false,
                refreshIntervalMs = 900_000L
            ),
            graph = graph
        )

        val snap = service.refresh()

        assertThat(snap.enabled).isTrue()
        assertThat(snap.groupNamePrefix).isEqualTo("Platform-System-")
        assertThat(snap.loadedAt).isNotNull()
        assertThat(service.lastLoadedAt()).isNotNull()
        assertThat(snap.groups).hasSize(2)
        assertThat(snap.groups.map { it.group.displayName })
            .containsExactly("Platform-System-Maintainer", "Platform-System-Reader")
        assertThat(snap.groups[0].members).hasSize(1)
        assertThat(snap.groups[1].members).hasSize(2)

        // Thread-safe map keyed by group object id
        val map = service.groupMembersMap()
        assertThat(map).hasSize(2)
        assertThat(map.keys).containsExactlyInAnyOrder("g-maint", "g-read")
        assertThat(service.membersOf("g-maint").map { it.id }).containsExactly("u1")
        assertThat(service.membersOf("g-read").map { it.id }).containsExactly("u1", "u2")
        assertThat(service.findGroup("g-maint")?.group?.displayName)
            .isEqualTo("Platform-System-Maintainer")

        // Unique members across groups
        assertThat(service.allMembers().map { it.id }).containsExactly("u1", "u2")

        val response = service.snapshotResponse()
        assertThat(response.groupCount).isEqualTo(2)
        assertThat(response.memberCount).isEqualTo(3)
        assertThat(response.groups[0].members[0].userPrincipalName).isEqualTo("alice@contoso.com")

        verify(graph).listGroupsByDisplayNamePrefix("Platform-System-")
        verify(graph, never()).listGroupMembers(eq("g-other"), any())
    }

    @Test
    fun `refresh replaces ConcurrentHashMap atomically and drops stale groups`() {
        val graph = mock<MicrosoftGraphClient>()
        whenever(graph.listGroupsByDisplayNamePrefix("Platform-System-"))
            .thenReturn(listOf(EntraGroup("g-old", "Platform-System-Old")))
            .thenReturn(listOf(EntraGroup("g-new", "Platform-System-New")))
        whenever(graph.listGroupMembers("g-old", false)).thenReturn(
            listOf(EntraDirectoryMember(id = "u-old", displayName = "Old"))
        )
        whenever(graph.listGroupMembers("g-new", false)).thenReturn(
            listOf(EntraDirectoryMember(id = "u-new", displayName = "New"))
        )

        val service = service(
            properties = EntraDirectoryProperties(enabled = true, groupNamePrefix = "Platform-System-"),
            graph = graph
        )

        service.refresh()
        assertThat(service.groupMembersMap().keys).containsExactly("g-old")

        service.refresh()
        assertThat(service.groupMembersMap().keys).containsExactly("g-new")
        assertThat(service.membersOf("g-old")).isEmpty()
        assertThat(service.membersOf("g-new").map { it.id }).containsExactly("u-new")
    }

    @Test
    fun `default refresh interval is 15 minutes`() {
        assertThat(EntraDirectoryProperties().refreshIntervalMs).isEqualTo(900_000L)
    }

    @Test
    fun `refresh uses transitive members when configured`() {
        val graph = mock<MicrosoftGraphClient>()
        whenever(graph.listGroupsByDisplayNamePrefix("Platform-System-")).thenReturn(
            listOf(EntraGroup("g1", "Platform-System-Ops"))
        )
        whenever(graph.listGroupMembers("g1", true)).thenReturn(
            listOf(EntraDirectoryMember(id = "nested-user", displayName = "Nested"))
        )

        val service = service(
            properties = EntraDirectoryProperties(
                enabled = true,
                groupNamePrefix = "Platform-System-",
                includeTransitiveMembers = true
            ),
            graph = graph
        )

        val snap = service.refresh()
        assertThat(snap.groups.single().members.single().id).isEqualTo("nested-user")
        verify(graph).listGroupMembers("g1", true)
    }

    @Test
    fun `member list failure for one group does not abort other groups`() {
        val graph = mock<MicrosoftGraphClient>()
        whenever(graph.listGroupsByDisplayNamePrefix("Platform-System-")).thenReturn(
            listOf(
                EntraGroup("g-bad", "Platform-System-Bad"),
                EntraGroup("g-ok", "Platform-System-Ok")
            )
        )
        whenever(graph.listGroupMembers("g-bad", false)).thenThrow(RuntimeException("Graph 403"))
        whenever(graph.listGroupMembers("g-ok", false)).thenReturn(
            listOf(EntraDirectoryMember(id = "u-ok", displayName = "Ok User"))
        )

        val service = service(
            properties = EntraDirectoryProperties(enabled = true, groupNamePrefix = "Platform-System-"),
            graph = graph
        )

        val snap = service.refresh()
        assertThat(snap.groups).hasSize(2)
        assertThat(snap.groups.first { it.group.id == "g-bad" }.members).isEmpty()
        assertThat(snap.groups.first { it.group.id == "g-ok" }.members).hasSize(1)
    }

    @Test
    fun `group list failure propagates from refresh`() {
        val graph = mock<MicrosoftGraphClient>()
        whenever(graph.listGroupsByDisplayNamePrefix(any())).thenThrow(RuntimeException("Graph down"))

        val service = service(
            properties = EntraDirectoryProperties(enabled = true),
            graph = graph
        )

        assertThatThrownBy { service.refresh() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("Graph down")
    }

    @Test
    fun `scheduled refresh skips when disabled`() {
        val graph = mock<MicrosoftGraphClient>()
        val service = service(
            properties = EntraDirectoryProperties(enabled = false, refreshIntervalMs = 300_000),
            graph = graph
        )
        service.scheduledRefresh()
        verify(graph, never()).listGroupsByDisplayNamePrefix(any())
    }

    @Test
    fun `scheduled refresh invokes graph when enabled`() {
        val graph = mock<MicrosoftGraphClient>()
        whenever(graph.listGroupsByDisplayNamePrefix(any())).thenReturn(emptyList())
        val service = service(
            properties = EntraDirectoryProperties(
                enabled = true,
                refreshIntervalMs = 60_000,
                groupNamePrefix = "Platform-System-"
            ),
            graph = graph
        )
        service.scheduledRefresh()
        verify(graph, times(1)).listGroupsByDisplayNamePrefix("Platform-System-")
    }

    private fun service(
        properties: EntraDirectoryProperties,
        graph: MicrosoftGraphClient?
    ): EntraGroupDirectoryService {
        val provider = mock<ObjectProvider<MicrosoftGraphClient>>()
        whenever(provider.getIfAvailable()).thenReturn(graph)
        return EntraGroupDirectoryService(properties, provider)
    }
}
