package org.jrtech.platformmanagement.config.azure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Shared Azure identity settings (`app.azure.credential`).
 *
 * One [clientId] covers both UAMI and service principal:
 * - env `APP_UAMI_CLIENT_ID` (UAMI), or
 * - env `AZURE_CLIENT_ID` when using the SP pair / Workload Identity UAMI
 *
 * Mode is inferred by [AzureCredentialFactory]:
 * 1. [clientId] + [clientSecret] → service principal (username + password)
 * 2. [clientId] only → user-assigned managed identity (UAMI)
 * 3. otherwise → system-assigned managed identity (SAMI)
 */
@ConfigurationProperties(prefix = "app.azure.credential")
data class AzureCredentialProperties(
    /**
     * Client id shared by UAMI and service principal.
     * Bound from `APP_UAMI_CLIENT_ID` or `AZURE_CLIENT_ID` (see application.yml).
     */
    val clientId: String = "",

    /**
     * Service principal client secret (`AZURE_CLIENT_SECRET`).
     * When set together with [clientId], service principal mode is used.
     */
    val clientSecret: String = "",

    /** Entra tenant GUID (`AZURE_TENANT_ID`). Required for service principal. */
    val tenantId: String = ""
) {
    fun clientIdOrEmpty(): String = clientId.trim()
    fun clientSecretOrEmpty(): String = clientSecret.trim()
    fun tenantIdOrEmpty(): String = tenantId.trim()

    fun hasClientId(): Boolean = clientIdOrEmpty().isNotEmpty()

    fun hasServicePrincipalPair(): Boolean =
        hasClientId() && clientSecretOrEmpty().isNotEmpty()
}
