package org.jrtech.platformmanagement.connectors.consumption.eventhub

import org.jrtech.platformmanagement.config.azure.AzureCredentialFactory
import org.jrtech.platformmanagement.config.azure.AzureCredentialProperties
import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.consumption.BusinessBodyDecoder
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.service.ConsumptionService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EventHubConnectorConfig {

    private val log = logger()

    @Bean
    fun eventHubProcessorRuntime(
        connectorsProperties: ConnectorsProperties,
        bodyDecoder: BusinessBodyDecoder,
        consumptionService: ConsumptionService,
        sharedCredential: AzureCredentialProperties
    ): EventHubProcessorRuntime {
        val props = connectorsProperties.consumptionEventHub
        return if (props.enabled && props.isConfigured()) {
            log.info(
                "Event Hub processor runtime: Azure (ns={} hub={})",
                props.fullyQualifiedNamespace,
                props.eventHubName
            )
            val credential =
                AzureCredentialFactory.create(sharedCredential, purpose = "consumption-eventhub")
            AzureEventHubProcessorRuntime(props, bodyDecoder, consumptionService, credential)
        } else {
            val reason = when {
                !props.enabled -> "disabled"
                else -> "incomplete-config"
            }
            log.info("Event Hub processor runtime: in-memory ({})", reason)
            InMemoryEventHubProcessorRuntime(label = reason)
        }
    }
}
