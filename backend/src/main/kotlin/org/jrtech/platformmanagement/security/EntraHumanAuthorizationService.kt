package org.jrtech.platformmanagement.security

import org.jrtech.platformmanagement.config.AppSecurityProperties
import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.entra.EntraGroupDirectoryService
import org.jrtech.platformmanagement.logging.logger
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

/**
 * Resolves API permission scopes for **human** callers from Entra group membership
 * using a **static lookup table** ([EntraGroupPermissionScopeTable]):
 *
 * ```
 * Platform-System-Maintainer → System.Maintainer
 * Platform-System-Reader → System.Reader
 * …
 * ```
 *
 * Lookup order (JWT claims + Graph cache):
 * 1. JWT identity (email / UPN / preferred_username / oid) against Graph-loaded members
 *    → group display name → [EntraGroupPermissionScopeTable]
 * 2. JWT `groups` claim (object ids) → directory display name → static table
 * 3. Optional config [AppSecurityProperties.groupRoleMappings] (object-id → roles)
 *
 * Technical users keep using the JWT `roles` claim directly.
 */
@Service
class EntraHumanAuthorizationService(
    private val directoryService: EntraGroupDirectoryService,
    private val directoryProperties: EntraDirectoryProperties,
    private val securityProperties: AppSecurityProperties
) {
    private val log = logger()

    /**
     * App role values for this JWT from platform group membership + static mappings.
     * Does **not** include the JWT `roles` claim itself (caller merges that separately).
     */
    fun resolveMembershipAppRoles(jwt: Jwt): List<String> {
        val roles = linkedSetOf<String>()

        // 1) Human email/UPN/oid membership in Platform-System-* groups (Graph cache)
        val fromMembership = directoryService.appRolesForHumanIdentity(
            emails = humanEmails(jwt),
            objectIds = humanObjectIds(jwt)
        )
        roles += fromMembership

        // 2) JWT groups claim → group id in cache → display name → role
        val groupIds = JwtAuthorityMapper.extractGroups(jwt)
        if (groupIds.isNotEmpty()) {
            roles += directoryService.appRolesForGroupIds(groupIds)
        }

        // 3) Static config maps (object id → roles)
        roles += JwtAuthorityMapper.resolveGroupRoleMappings(jwt, groupIds, securityProperties)

        if (roles.isNotEmpty() && log.isDebugEnabled) {
            log.debug(
                "Human group→role resolution subject={} emails={} → roles={}",
                jwt.subject,
                humanEmails(jwt),
                roles
            )
        }
        return roles.toList()
    }

    /**
     * Platform group display names the human belongs to (from Graph membership cache
     * and/or JWT group ids resolved through the cache).
     */
    fun resolvePlatformGroupNames(jwt: Jwt): List<String> {
        val names = linkedSetOf<String>()
        names += directoryService.groupDisplayNamesForHumanIdentity(
            emails = humanEmails(jwt),
            objectIds = humanObjectIds(jwt)
        )
        for (groupId in JwtAuthorityMapper.extractGroups(jwt)) {
            directoryService.findGroup(groupId)?.group?.displayName?.let { names += it }
        }
        return names.toList()
    }

    /**
     * Effective app roles: JWT `roles` + membership-mapped roles.
     */
    fun extractEffectiveRoles(jwt: Jwt): List<String> {
        val roles = linkedSetOf<String>()
        JwtAuthorityMapper.extractRoles(jwt).forEach { roles += it }
        resolveMembershipAppRoles(jwt).forEach { roles += it }
        return roles.toList()
    }

    /**
     * Full authority set for a JWT: scopes, JWT roles, membership roles, GROUP_*.
     */
    fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = linkedSetOf<GrantedAuthority>()
        // Base claim mapping (scopes, roles claim, GROUP_*, static groupRoleMappings)
        authorities += JwtAuthorityMapper.extractAuthorities(jwt, securityProperties)

        for (role in resolveMembershipAppRoles(jwt)) {
            val normalized = if (role.startsWith("ROLE_")) role else "ROLE_$role"
            authorities += SimpleGrantedAuthority(normalized)
            if (!role.startsWith("ROLE_")) {
                authorities += SimpleGrantedAuthority(role)
            }
        }

        for (groupName in resolvePlatformGroupNames(jwt)) {
            authorities += SimpleGrantedAuthority("GROUP_NAME_$groupName")
        }
        return authorities
    }

    fun humanEmails(jwt: Jwt): Set<String> {
        val emails = linkedSetOf<String>()
        for (claim in listOf("preferred_username", "email", "upn", "unique_name")) {
            jwt.getClaimAsString(claim)?.trim()?.takeIf { it.isNotEmpty() && looksLikeEmailOrUpn(it) }
                ?.let { emails += it }
        }
        return emails
    }

    fun humanObjectIds(jwt: Jwt): Set<String> {
        val ids = linkedSetOf<String>()
        jwt.getClaimAsString("oid")?.trim()?.takeIf { it.isNotEmpty() }?.let { ids += it }
        // For user tokens, sub is often the same as oid; include for membership match.
        // Skip pure app tokens later if needed; matching a non-member oid is harmless.
        jwt.subject?.trim()?.takeIf { it.isNotEmpty() }?.let { ids += it }
        return ids
    }

    private fun looksLikeEmailOrUpn(value: String): Boolean =
        value.contains('@') || value.contains('#') // guest UPNs use #EXT#
}
