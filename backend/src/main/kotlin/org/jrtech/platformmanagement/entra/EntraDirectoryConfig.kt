package org.jrtech.platformmanagement.entra

import com.azure.core.credential.TokenCredential
import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.config.azure.AzureCredentialFactory
import org.jrtech.platformmanagement.config.azure.AzureCredentialProperties
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
 *
 * Auth uses shared [AzureCredentialFactory] (UAMI / service principal / SAMI).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(EntraDirectoryProperties::class)
class EntraDirectoryConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.entra-directory", name = ["enabled"], havingValue = "true")
    fun microsoftGraphTokenCredential(
        sharedCredential: AzureCredentialProperties
    ): TokenCredential =
        AzureCredentialFactory.create(sharedCredential, purpose = "Entra directory Graph")

    @Bean
    @ConditionalOnProperty(prefix = "app.entra-directory", name = ["enabled"], havingValue = "true")
    fun microsoftGraphClient(
        properties: EntraDirectoryProperties,
        credential: TokenCredential
    ): MicrosoftGraphClient = MicrosoftGraphClientImpl(properties, credential)
}
