package com.example.platformmanagement.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Maps Microsoft Entra access-token claims to Spring Security authorities.
 *
 * - `scp` / `scope` → `SCOPE_<name>` (also adds short name when scope is a full URI)
 * - `roles` → `ROLE_<appRole>` (e.g. `ROLE_System.Reader`)
 *
 * Robust against claim shape differences (list vs single string, full URI scopes).
 */
object JwtAuthorityMapper {

    fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = linkedSetOf<GrantedAuthority>()

        for (scope in extractScopes(jwt)) {
            authorities += SimpleGrantedAuthority("SCOPE_$scope")
            val shortName = scope.substringAfterLast('/')
            if (shortName.isNotEmpty() && shortName != scope) {
                authorities += SimpleGrantedAuthority("SCOPE_$shortName")
            }
        }

        for (role in extractRoles(jwt)) {
            val normalized = if (role.startsWith("ROLE_")) role else "ROLE_$role"
            authorities += SimpleGrantedAuthority(normalized)
            // Also expose bare value for flexible matching
            if (!role.startsWith("ROLE_")) {
                authorities += SimpleGrantedAuthority(role)
            }
        }

        return authorities
    }

    fun extractRoles(jwt: Jwt): List<String> = claimAsStringList(jwt, "roles")

    fun extractScopes(jwt: Jwt): List<String> {
        val raw = jwt.getClaimAsString("scp")
            ?: jwt.getClaimAsString("scope")
            ?: return emptyList()
        return raw.split(' ', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
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
                    // rare: JSON array as string
                    t.trim('[', ']').split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
                } else {
                    listOf(t)
                }
            }
            else -> listOf(value.toString().trim()).filter { it.isNotEmpty() }
        }
    }
}
