package com.example.platformmanagement.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Application security toggles for Microsoft Entra ID (Azure AD) resource-server mode.
 *
 * Bound from `app.security.*` in application.yml.
 * Production always keeps [permitAll] false (JWT + app roles required).
 * Tests may set permit-all=true in application-test.yml for non-security slices.
 */
@ConfigurationProperties(prefix = "app.security")
data class AppSecurityProperties(
    /**
     * When true, API endpoints do not require a JWT.
     * Default and runtime: false. Only the `test` profile should enable this.
     */
    val permitAll: Boolean = false,

    /**
     * Optional delegated scope (scp) or app role that must be present on the token.
     * Examples: `access_as_user`, `Participant.Read`.
     * Mapped authorities are `SCOPE_<name>` and `ROLE_<name>`.
     */
    val requiredScope: String = "",

    /**
     * Browser origins allowed for CORS (SPA frontends calling this API with Bearer tokens).
     */
    val corsAllowedOrigins: List<String> = listOf("http://localhost:3000")
)
