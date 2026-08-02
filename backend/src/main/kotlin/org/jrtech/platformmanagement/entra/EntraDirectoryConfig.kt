package org.jrtech.platformmanagement.entra

import com.azure.core.credential.TokenCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.identity.DefaultAzureCredentialBuilder
import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.logging.logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Graph client wiring for Entra directory loading.
 *
 * Periodic refresh is **not** registered here — it is owned by
 * [org.jrtech.platformmanagement.connectors.entra.EntraDirectoryConnector]
 * start/stop lifecycle (`TaskScheduler` + cancelable future for *next* runs only).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(EntraDirectoryProperties::class)
class EntraDirectoryConfig {

    private val log = logger()

    @Bean
    @ConditionalOnProperty(prefix = "app.entra-directory", name = ["enabled"], havingValue = "true")
    fun microsoftGraphTokenCredential(properties: EntraDirectoryProperties): TokenCredential {
        val tenantId = properties.tenantId.trim()
        val clientId = properties.clientId.trim()
        val clientSecret = properties.clientSecret.trim()

        if (tenantId.isNotEmpty() && clientId.isNotEmpty() && clientSecret.isNotEmpty()) {
            log.info(
                "Entra directory Graph auth: client credentials (tenant={}, clientId={})",
                tenantId,
                clientId
            )
            return ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build()
        }

        log.info(
            "Entra directory Graph auth: DefaultAzureCredential " +
                "(set app.entra-directory.client-secret for client credentials)"
        )
        return DefaultAzureCredentialBuilder().build()
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.entra-directory", name = ["enabled"], havingValue = "true")
    fun microsoftGraphClient(
        properties: EntraDirectoryProperties,
        credential: TokenCredential
    ): MicrosoftGraphClient = MicrosoftGraphClientImpl(properties, credential)
}
