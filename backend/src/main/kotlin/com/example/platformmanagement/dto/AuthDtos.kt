package com.example.platformmanagement.dto

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
    /** Application (client) id that obtained the token (`azp` or `appid`). */
    val clientId: String?,
    /** Tenant id (`tid`). */
    val tenantId: String?,
    /** Audience(s) the token was issued for (`aud`). */
    val audience: List<String>,
    /** Spring Security authorities mapped from `scp`/`scope` and `roles`. */
    val authorities: List<String>,
    /** Token scopes from the `scp` claim (delegated). */
    val scopes: List<String>,
    /** App roles from the `roles` claim. */
    val roles: List<String>
)
