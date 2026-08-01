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
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.time.Duration

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

    /**
     * Rebuilds the ConcurrentHashMap of Entra group → members from Microsoft Graph
     * on a fixed delay (default 15 minutes). Only registered when directory loading
     * is enabled and [EntraDirectoryProperties.refreshIntervalMs] is positive.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.entra-directory", name = ["enabled"], havingValue = "true")
    fun entraDirectorySchedulingConfigurer(
        properties: EntraDirectoryProperties,
        directoryService: EntraGroupDirectoryService
    ): SchedulingConfigurer = SchedulingConfigurer { taskRegistrar: ScheduledTaskRegistrar ->
        val intervalMs = properties.refreshIntervalMs
        if (intervalMs > 0L) {
            val minutes = intervalMs / 60_000.0
            log.info(
                "Scheduling Entra group→member ConcurrentHashMap refresh every {} ms (~{} min)",
                intervalMs,
                String.format("%.1f", minutes)
            )
            taskRegistrar.addFixedDelayTask(
                { directoryService.scheduledRefresh() },
                Duration.ofMillis(intervalMs)
            )
        } else {
            log.info("Entra directory periodic refresh disabled (refresh-interval-ms <= 0)")
        }
    }
}
