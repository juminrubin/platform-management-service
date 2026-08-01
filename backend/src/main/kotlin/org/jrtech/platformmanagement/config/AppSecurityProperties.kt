package org.jrtech.platformmanagement.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Application security toggles for Microsoft Entra ID (Azure AD) resource-server mode.
 *
 * Bound from `app.security.*` in application.yml.
 * Production always keeps [permitAll] false (JWT + app roles required).
 * Tests may set permit-all=true in application-test.yml for non-security slices.
 *
 * ## Group & scope mapping from Application Registration
 *
 * Prefer Entra **app role assignments** (JWT `roles`) for authorization.
 * Optionally map Entra **security group** object IDs (JWT `groups`) to app roles when
 * tokens carry groups without resolved app roles — either globally via [groupRoleMappings]
 * or scoped to an Application Registration via [applicationRegistrations].
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
    val corsAllowedOrigins: List<String> = listOf("http://localhost:3000"),

    /**
     * Global map of Entra security **group object ID** → API app role values.
     *
     * When a JWT includes a matching id in the `groups` claim, those roles are
     * synthesized as Spring authorities (`ROLE_System.Reader`, …) in addition to
     * any `roles` claim values already present.
     *
     * Example YAML:
     * ```
     * app.security.group-role-mappings:
     *   "11111111-2222-3333-4444-555555555555":
     *     - System.Maintainer
     * ```
     */
    val groupRoleMappings: Map<String, List<String>> = emptyMap(),

    /**
     * Per **Application Registration** (client id) refinements for OAuth scopes and
     * group→role mapping. Matched against token `azp`, `appid`, and `aud` claims.
     *
     * Use this when multiple client apps call the API and group-to-role rules or
     * documented OAuth scopes differ by Application Registration ID.
     */
    val applicationRegistrations: List<ApplicationRegistrationSecurity> = emptyList()
)

/**
 * Security mapping for a single Entra Application Registration.
 *
 * [clientId] is the Application (client) ID from the app registration blade.
 */
data class ApplicationRegistrationSecurity(
    /** Application (client) ID of the Entra app registration. */
    val clientId: String = "",

    /**
     * Group object ID → app role values that apply only when the token's
     * `azp` / `appid` / `aud` matches [clientId]. Merged with global
     * [AppSecurityProperties.groupRoleMappings] (registration-specific wins on conflict
     * by appending roles; duplicates are removed).
     */
    val groupRoleMappings: Map<String, List<String>> = emptyMap(),

    /**
     * OAuth permission scopes associated with this Application Registration
     * (e.g. `access_as_user`, or full `api://{client-id}/access_as_user`).
     *
     * Used for diagnostics on `/api/v1/auth/me` (expected scopes) and for
     * optional matching helpers — not a hard authorization gate by itself.
     * Use [AppSecurityProperties.requiredScope] for a tenant-wide scope gate.
     */
    val oauthScopes: List<String> = emptyList()
)
