package org.jrtech.platformmanagement.config.azure

import com.azure.core.credential.TokenCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.identity.ManagedIdentityCredentialBuilder
import org.jrtech.platformmanagement.logging.logger

/**
 * Builds an Azure [TokenCredential] from [AzureCredentialProperties].
 *
 * Single [AzureCredentialProperties.clientId] is used for both UAMI and service principal.
 * Mode is inferred:
 *
 * 1. **Service principal** — [clientId] + [clientSecret] both set
 *    (username = client id, password = client secret; also needs [tenantId])
 * 2. **UAMI** — [clientId] set, no client secret
 * 3. **SAMI** — no client id
 */
object AzureCredentialFactory {

    private val log = logger()

    enum class Mode {
        SERVICE_PRINCIPAL,
        UAMI,
        SAMI
    }

    fun create(
        properties: AzureCredentialProperties,
        purpose: String = "Azure"
    ): TokenCredential =
        when (resolve(properties)) {
            Mode.SERVICE_PRINCIPAL -> createServicePrincipal(properties, purpose)
            Mode.UAMI -> createUami(properties, purpose)
            Mode.SAMI -> createSami(purpose)
        }

    /**
     * 1. client id + client secret → service principal
     * 2. client id only → UAMI
     * 3. otherwise → SAMI
     */
    fun resolve(properties: AzureCredentialProperties): Mode = when {
        properties.hasServicePrincipalPair() -> Mode.SERVICE_PRINCIPAL
        properties.hasClientId() -> Mode.UAMI
        else -> Mode.SAMI
    }

    private fun createServicePrincipal(
        properties: AzureCredentialProperties,
        purpose: String
    ): TokenCredential {
        val tenantId = properties.tenantIdOrEmpty()
        val clientId = properties.clientIdOrEmpty()
        require(tenantId.isNotEmpty()) {
            "Service principal requires AZURE_TENANT_ID / app.azure.credential.tenant-id " +
                "when client-id and client-secret are set"
        }
        log.info(
            "{} auth: service principal (client id + secret) tenant={} clientId={}",
            purpose,
            tenantId,
            clientId
        )
        return ClientSecretCredentialBuilder()
            .tenantId(tenantId)
            .clientId(clientId)
            .clientSecret(properties.clientSecretOrEmpty())
            .build()
    }

    private fun createUami(
        properties: AzureCredentialProperties,
        purpose: String
    ): TokenCredential {
        val clientId = properties.clientIdOrEmpty()
        log.info("{} auth: UAMI (user-assigned managed identity) clientId={}", purpose, clientId)
        return ManagedIdentityCredentialBuilder()
            .clientId(clientId)
            .build()
    }

    private fun createSami(purpose: String): TokenCredential {
        log.info("{} auth: SAMI (system-assigned managed identity)", purpose)
        return ManagedIdentityCredentialBuilder().build()
    }
}
