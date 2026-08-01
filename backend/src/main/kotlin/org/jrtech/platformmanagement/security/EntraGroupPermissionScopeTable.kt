package org.jrtech.platformmanagement.security

/**
 * Simple **static lookup table**: Entra security group → API permission scopes.
 *
 * Used when resolving human authorization from JWT-related group membership
 * (Graph-loaded members matched by email/UPN/oid, or JWT `groups` claim object ids
 * resolved to display names via the directory cache).
 *
 * Permission scopes here are the same values as Entra **app roles** / Spring
 * `ROLE_*` authorities checked by `@PreAuthorize` / [Authz]
 * (e.g. `System.Maintainer`).
 *
 * Edit this table when adding a new `Platform-System-*` group.
 */
object EntraGroupPermissionScopeTable {

    /**
     * Entra group **display name** → permission scope values.
     *
     * Keys must match the group name in Microsoft Entra exactly
     * (case-insensitive lookup is also attempted).
     */
    val BY_GROUP_DISPLAY_NAME: Map<String, String> = mapOf(
        "Platform-System-Maintainer" to AppRoles.SYSTEM_MAINTAINER,
        "Platform-System-Reader" to AppRoles.SYSTEM_READER,
        "Platform-System-Entitlement-Reader" to AppRoles.ENTITLEMENT_READER,
        "Platform-System-Consumption-Registrator" to AppRoles.CONSUMPTION_REGISTRATOR
    )

    /** Case-insensitive index built once from [BY_GROUP_DISPLAY_NAME]. */
    private val byGroupDisplayNameIgnoreCase: Map<String, String> =
        BY_GROUP_DISPLAY_NAME.mapKeys { it.key.lowercase() }

    /**
     * Returns permission scopes for one Entra group display name, or empty if unknown.
     */
    fun scopeForGroupDisplayName(displayName: String): String {
        val name = displayName.trim()
        if (name.isEmpty()) return ""
        return BY_GROUP_DISPLAY_NAME[name]
            ?: byGroupDisplayNameIgnoreCase[name.lowercase()]
            ?: ""
    }

    /**
     * First permission scope for [displayName], or null if the group is not in the table.
     * Convenient when each group maps to a single role (as in the default table).
     */
    fun primaryScopeForGroupDisplayName(displayName: String): String =
        scopeForGroupDisplayName(displayName)

    /**
     * Union of permission scopes for many group display names (order-preserving, unique).
     */
    fun scopesForGroupDisplayNames(displayNames: Collection<String>): List<String> {
        val scopes = linkedSetOf<String>()
        for (name in displayNames) {
            val scope = scopeForGroupDisplayName(name)
            if (!scope.isEmpty()) scopes.add(scope)
        }
        return scopes.toList()
    }

    /**
     * Whether [displayName] is listed in the static table (regardless of scopes).
     */
    fun isKnownGroupDisplayName(displayName: String): Boolean =
        scopeForGroupDisplayName(displayName).isNotEmpty()
}
