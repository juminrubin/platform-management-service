package org.jrtech.platformmanagement.dto

/**
 * Authenticated caller identity derived from a Microsoft Entra ID access token.
 */
data class AuthenticatedUserResponse(
    /** Subject claim (`sub`) — stable Entra object/principal id. */
    val subject: String,
    /** Preferred username (UPN/email for users) when present on the token. */
    val preferredUsername: String?,
    /** Display name (`name`) when present. */
    val name: String?,
    /**
     * Application Registration (client) id that obtained the token (`azp` or `appid`).
     * For human SPA/CLI tokens this is the public client; for MI/SP it is the app id.
     */
    val clientId: String?,
    /** Tenant id (`tid`). */
    val tenantId: String?,
    /** Audience(s) the token was issued for (`aud`) — typically the API app registration id. */
    val audience: List<String>,
    /** Spring Security authorities mapped from `scp`/`scope`, `roles`, and `groups`. */
    val authorities: List<String>,
    /** Token scopes from the `scp` / `scope` claim (delegated OAuth permission scopes). */
    val scopes: List<String>,
    /**
     * Effective app roles: JWT `roles` claim plus roles synthesized from Entra security
     * groups (Platform-System-* membership for humans, and static group-id mappings).
     */
    val roles: List<String>,
    /**
     * Entra security group object IDs from the JWT `groups` claim (empty when not emitted
     * or when Entra resolved groups into app roles only).
     */
    val groups: List<String> = emptyList(),
    /**
     * Platform group display names resolved for this principal
     * (e.g. `Platform-System-Maintainer`) from Graph membership cache and/or JWT groups.
     */
    val platformGroups: List<String> = emptyList(),
    /**
     * OAuth permission scopes configured for Application Registration(s) that match
     * this token's client/audience ids (`app.security.application-registrations`).
     */
    val expectedScopes: List<String> = emptyList(),
    /**
     * Application Registration client ids that matched configured
     * `app.security.application-registrations` entries for this token.
     */
    val matchedApplicationRegistrationIds: List<String> = emptyList()
)
