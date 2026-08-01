package org.jrtech.platformmanagement.entra

import java.time.Instant

data class EntraGroup(
    val id: String,
    val displayName: String,
    val description: String? = null
)

data class EntraDirectoryMember(
    val id: String,
    val displayName: String? = null,
    val userPrincipalName: String? = null,
    val mail: String? = null,
    val odataType: String? = null
)

data class EntraGroupWithMembers(
    val group: EntraGroup,
    val members: List<EntraDirectoryMember>
)

data class EntraDirectorySnapshot(
    val enabled: Boolean,
    val groupNamePrefix: String,
    val loadedAt: Instant?,
    val groups: List<EntraGroupWithMembers>
) {
    val groupCount: Int get() = groups.size
    val memberCount: Int get() = groups.sumOf { it.members.size }
}
