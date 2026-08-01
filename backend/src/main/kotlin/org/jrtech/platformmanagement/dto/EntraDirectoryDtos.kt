package org.jrtech.platformmanagement.dto

import java.time.Instant

/**
 * Snapshot of Entra security groups whose names match the configured prefix
 * (default `Platform-System-`) and their members.
 */
data class EntraDirectorySnapshotResponse(
    val enabled: Boolean,
    val groupNamePrefix: String,
    val loadedAt: Instant?,
    val groupCount: Int,
    val memberCount: Int,
    val groups: List<EntraGroupWithMembersResponse>
)

data class EntraGroupWithMembersResponse(
    val id: String,
    val displayName: String,
    val description: String?,
    val members: List<EntraGroupMemberResponse>
)

data class EntraGroupMemberResponse(
    val id: String,
    val displayName: String?,
    val userPrincipalName: String?,
    val mail: String?,
    /** Graph `@odata.type`, e.g. `#microsoft.graph.user` or `#microsoft.graph.group`. */
    val odataType: String?
)
