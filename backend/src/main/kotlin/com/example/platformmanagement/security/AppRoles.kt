package com.example.platformmanagement.security

/**
 * Microsoft Entra ID **app role** values for this API.
 *
 * Create matching App roles on the API app registration (value must match exactly).
 *
 * Assignment (recommended — works for humans and technical accounts):
 * - **Humans:** assign the role to an Entra **security group**, put users in that group.
 *   Entra puts the role value into the access-token `roles` claim when the user
 *   requests a token for this API (delegated scope, e.g. access_as_user).
 * - **Managed Identity / SP:** assign the role to the identity’s service principal.
 *   Application tokens use scope `api://{client-id}/.default` and also carry `roles`.
 *
 * JWT claim: `"roles": ["System.Maintainer", ...]`
 * Spring authority: `ROLE_System.Maintainer` (see SecurityConfig authority mapping).
 *
 * See README “Recommended authorization model (human + technical)”.
 */
object AppRoles {
    /**
     * Admin / system maintainer — full CRUD on all resources.
     * Typically assigned to a security group of human admins.
     */
    const val SYSTEM_MAINTAINER = "System.Maintainer"

    /**
     * Read-only access to all list/get endpoints across controllers.
     * No create, update, or delete.
     */
    const val SYSTEM_READER = "System.Reader"

    /**
     * May check whether a caller identity is entitled to a service offering
     * (human users via group, or systems performing entitlement lookups).
     * Does **not** grant general read of admin list APIs (use [SYSTEM_READER] for that).
     */
    const val ENTITLEMENT_READER = "Entitlement.Reader"

    /**
     * Technical account (managed identity / service principal) that registers
     * token consumption for a caller identity against a service offering.
     */
    const val CONSUMPTION_REGISTRATOR = "Consumption.Registrator"
}
