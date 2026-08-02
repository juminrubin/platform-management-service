package org.jrtech.platformmanagement.security

import org.jrtech.platformmanagement.config.AppSecurityProperties
import org.jrtech.platformmanagement.config.ApplicationRegistrationSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Maps Microsoft Entra access-token claims to Spring Security authorities.
 *
 * - `scp` / `scope` → `SCOPE_<name>` (also adds short name when scope is a full URI)
 * - `roles` → `ROLE_<appRole>` (e.g. `ROLE_System.Reader`)
 * - `groups` → `GROUP_<objectId>` plus optional mapped `ROLE_*` from
 *   [AppSecurityProperties.groupRoleMappings] / per Application Registration mappings
 *
 * Human Entra **group display name → permission scope** mapping lives in the static
 * table [EntraGroupPermissionScopeTable] (applied via [EntraHumanAuthorizationService]
 * after group membership is resolved from JWT claims + Graph cache).
 *
 * Robust against claim shape differences (list vs single string, full URI scopes).
 */
object JwtAuthorityMapper {

    fun extractAuthorities(
        jwt: Jwt,
        securityProperties: AppSecurityProperties = AppSecurityProperties()
    ): Collection<GrantedAuthority> {
        val authorities = linkedSetOf<GrantedAuthority>()

        for (scope in extractScopes(jwt)) {
            addScopeAuthorities(authorities, scope)
        }

        for (role in extractRoles(jwt)) {
            addRoleAuthorities(authorities, role)
        }

        val groups = extractGroups(jwt)
        for (groupId in groups) {
            authorities += SimpleGrantedAuthority("GROUP_$groupId")
        }

        val resolvedGroupRoles = resolveGroupRoleMappings(jwt, groups, securityProperties)
        for (role in resolvedGroupRoles) {
            addRoleAuthorities(authorities, role)
        }

        return authorities
    }

    fun extractRoles(jwt: Jwt): List<String> = claimAsStringList(jwt, "roles")

    fun extractGroups(jwt: Jwt): List<String> = claimAsStringList(jwt, "groups")

    /**
     * Delegated scopes from `scp` (space-delimited string) or `scope` (string or list).
     * Also accepts a rare list-shaped `scp` claim.
     */
    fun extractScopes(jwt: Jwt): List<String> {
        val fromScp = claimAsScopeList(jwt, "scp")
        if (fromScp.isNotEmpty()) {
            return fromScp
        }
        return claimAsScopeList(jwt, "scope")
    }

    /**
     * Application registration / client identifiers present on the token:
     * `azp`, `appid`, and each `aud` value (bare client id and `api://…` forms).
     */
    fun extractApplicationRegistrationIds(jwt: Jwt): Set<String> {
        val ids = linkedSetOf<String>()
        jwt.getClaimAsString("azp")?.trim()?.takeIf { it.isNotEmpty() }?.let { ids += it }
        jwt.getClaimAsString("appid")?.trim()?.takeIf { it.isNotEmpty() }?.let { ids += it }
        for (aud in jwt.audience.orEmpty()) {
            val trimmed = aud.trim()
            if (trimmed.isEmpty()) continue
            ids += trimmed
            // Also index bare id when audience is api://{client-id}
            if (trimmed.startsWith("api://")) {
                val bare = trimmed.removePrefix("api://").substringBefore('/')
                if (bare.isNotEmpty()) ids += bare
            }
        }
        return ids
    }

    /**
     * Application registrations from config that match this token's client/audience ids.
     */
    fun matchingApplicationRegistrations(
        jwt: Jwt,
        securityProperties: AppSecurityProperties
    ): List<ApplicationRegistrationSecurity> {
        val tokenIds = extractApplicationRegistrationIds(jwt)
        if (tokenIds.isEmpty()) return emptyList()
        return securityProperties.applicationRegistrations.filter { reg ->
            val id = reg.clientId.trim()
            id.isNotEmpty() && (id in tokenIds || "api://$id" in tokenIds)
        }
    }

    /**
     * Effective group object-id → app-role list for this token
     * (global map + matching Application Registration maps).
     */
    fun resolveGroupRoleMappingTable(
        jwt: Jwt,
        securityProperties: AppSecurityProperties
    ): Map<String, List<String>> {
        val merged = linkedMapOf<String, MutableList<String>>()

        fun putAll(source: Map<String, List<String>>) {
            for ((groupId, roles) in source) {
                val key = groupId.trim()
                if (key.isEmpty()) continue
                val bucket = merged.getOrPut(key) { mutableListOf() }
                for (role in roles) {
                    val r = role.trim()
                    if (r.isNotEmpty() && r !in bucket) bucket += r
                }
            }
        }

        putAll(securityProperties.groupRoleMappings)
        for (reg in matchingApplicationRegistrations(jwt, securityProperties)) {
            putAll(reg.groupRoleMappings)
        }
        return merged.mapValues { it.value.toList() }
    }

    /**
     * App roles synthesized from JWT `groups` via configured mappings.
     */
    fun resolveGroupRoleMappings(
        jwt: Jwt,
        groups: List<String> = extractGroups(jwt),
        securityProperties: AppSecurityProperties = AppSecurityProperties()
    ): List<String> {
        if (groups.isEmpty()) return emptyList()
        val table = resolveGroupRoleMappingTable(jwt, securityProperties)
        if (table.isEmpty()) return emptyList()
        val roles = linkedSetOf<String>()
        for (groupId in groups) {
            table[groupId]?.forEach { roles += it }
        }
        return roles.toList()
    }

    /**
     * Effective roles for authorization: JWT `roles` plus roles mapped from `groups`.
     */
    fun extractEffectiveRoles(
        jwt: Jwt,
        securityProperties: AppSecurityProperties = AppSecurityProperties()
    ): List<String> {
        val roles = linkedSetOf<String>()
        extractRoles(jwt).forEach { roles += it }
        resolveGroupRoleMappings(jwt, securityProperties = securityProperties).forEach { roles += it }
        return roles.toList()
    }

    /**
     * OAuth scopes configured for Application Registrations that match this token.
     */
    fun expectedOauthScopes(
        jwt: Jwt,
        securityProperties: AppSecurityProperties
    ): List<String> {
        val scopes = linkedSetOf<String>()
        for (reg in matchingApplicationRegistrations(jwt, securityProperties)) {
            reg.oauthScopes.map { it.trim() }.filter { it.isNotEmpty() }.forEach { scopes += it }
        }
        return scopes.toList()
    }

    /**
     * Whether granted authorities satisfy [requiredScope]
     * (e.g. `access_as_user` or `api://…/access_as_user`).
     */
    fun matchesRequiredScope(authorities: Collection<String>, requiredScope: String): Boolean {
        val required = requiredScope.trim()
        if (required.isEmpty()) return true
        val shortRequired = required.substringAfterLast('/')
        val candidates = setOf(
            "SCOPE_$required",
            "ROLE_$required",
            required,
            "SCOPE_$shortRequired",
            "ROLE_$shortRequired",
            shortRequired
        )
        return authorities.any { it in candidates } ||
            authorities.any { auth ->
                auth.removePrefix("SCOPE_").removePrefix("ROLE_").substringAfterLast('/') == shortRequired
            }
    }

    private fun addScopeAuthorities(authorities: MutableSet<GrantedAuthority>, scope: String) {
        authorities += SimpleGrantedAuthority("SCOPE_$scope")
        val shortName = scope.substringAfterLast('/')
        if (shortName.isNotEmpty() && shortName != scope) {
            authorities += SimpleGrantedAuthority("SCOPE_$shortName")
        }
        // Expose `api://{id}/.default` short form for application tokens
        if (scope.endsWith("/.default") || scope == ".default") {
            authorities += SimpleGrantedAuthority("SCOPE_.default")
        }
    }

    private fun addRoleAuthorities(authorities: MutableSet<GrantedAuthority>, role: String) {
        val trimmed = role.trim()
        if (trimmed.isEmpty()) return
        val normalized = if (trimmed.startsWith("ROLE_")) trimmed else "ROLE_$trimmed"
        authorities += SimpleGrantedAuthority(normalized)
        if (!trimmed.startsWith("ROLE_")) {
            authorities += SimpleGrantedAuthority(trimmed)
        }
    }

    private fun claimAsScopeList(jwt: Jwt, claim: String): List<String> {
        val value = jwt.claims[claim] ?: return emptyList()
        return when (value) {
            is String -> splitScopeString(value)
            is List<*> -> value.flatMap { item ->
                when (item) {
                    null -> emptyList()
                    is String -> splitScopeString(item)
                    else -> splitScopeString(item.toString())
                }
            }
            is Collection<*> -> value.flatMap { item ->
                when (item) {
                    null -> emptyList()
                    is String -> splitScopeString(item)
                    else -> splitScopeString(item.toString())
                }
            }
            is Array<*> -> value.flatMap { item ->
                when (item) {
                    null -> emptyList()
                    is String -> splitScopeString(item)
                    else -> splitScopeString(item.toString())
                }
            }
            else -> splitScopeString(value.toString())
        }.distinct()
    }

    private fun splitScopeString(raw: String): List<String> {
        val t = raw.trim()
        if (t.isEmpty()) return emptyList()
        // Rare: JSON array encoded as a string
        if (t.startsWith("[")) {
            return t.trim('[', ']')
                .split(',')
                .map { it.trim().trim('"').trim('\'') }
                .filter { it.isNotEmpty() }
        }
        return t.split(' ', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun claimAsStringList(jwt: Jwt, claim: String): List<String> {
        val value = jwt.claims[claim] ?: return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            is Collection<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            is Array<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            is String -> {
                val t = value.trim()
                if (t.isEmpty()) emptyList()
                else if (t.startsWith("[")) {
                    t.trim('[', ']').split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
                } else {
                    // Single GUID or space-delimited group list
                    t.split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
                }
            }
            else -> listOf(value.toString().trim()).filter { it.isNotEmpty() }
        }
    }
}
