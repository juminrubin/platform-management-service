package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.logging.logger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ConsumptionBlobConfig {

    private val log = logger()

    @Bean
    fun consumptionBlobStorageClient(
        connectorsProperties: ConnectorsProperties
    ): ConsumptionBlobStorageClient? {
        val props = connectorsProperties.consumptionBlob
        if (!props.enabled || !props.isConfigured()) {
            log.info(
                "Consumption blob storage client not created (enabled={}, configured={})",
                props.enabled,
                props.isConfigured()
            )
            return null
        }
        return AzureConsumptionBlobStorageClient(props)
    }
}
