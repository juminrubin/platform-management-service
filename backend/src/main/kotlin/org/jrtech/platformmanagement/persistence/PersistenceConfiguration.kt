package org.jrtech.platformmanagement.persistence

import org.jrtech.platformmanagement.config.azure.AzureCredentialProperties
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.AzureTableBlobFileClaimStore
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.BlobFileClaimStore
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.InMemoryBlobFileClaimStore
import org.jrtech.platformmanagement.persistence.memory.InMemoryParticipantCallConsumptionRepository
import org.jrtech.platformmanagement.persistence.memory.InMemoryParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.persistence.memory.InMemoryParticipantRepository
import org.jrtech.platformmanagement.persistence.memory.InMemoryParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.persistence.memory.InMemoryServiceOfferingRepository
import org.jrtech.platformmanagement.persistence.table.AzureTableCallerRegistrationRepository
import org.jrtech.platformmanagement.persistence.table.AzureTableConsumptionRepository
import org.jrtech.platformmanagement.persistence.table.AzureTableEntitlementRepository
import org.jrtech.platformmanagement.persistence.table.AzureTableParticipantRepository
import org.jrtech.platformmanagement.persistence.table.AzureTableServiceOfferingRepository
import org.jrtech.platformmanagement.repository.ParticipantCallConsumptionRepository
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AzureTableProperties::class)
class PersistenceConfiguration {

    private val log = logger()

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun inMemoryPlatformStore(): InMemoryPlatformStore {
        log.info("Persistence: using in-memory platform store (app.azure-table.enabled=false)")
        return InMemoryPlatformStore()
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun participantRepository(store: InMemoryPlatformStore): ParticipantRepository =
        InMemoryParticipantRepository(store)

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun serviceOfferingRepository(store: InMemoryPlatformStore): ServiceOfferingRepository =
        InMemoryServiceOfferingRepository(store)

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun participantCallerRegistrationRepository(
        store: InMemoryPlatformStore
    ): ParticipantCallerRegistrationRepository =
        InMemoryParticipantCallerRegistrationRepository(store)

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun participantServiceEntitlementRepository(
        store: InMemoryPlatformStore
    ): ParticipantServiceEntitlementRepository =
        InMemoryParticipantServiceEntitlementRepository(store)

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun participantCallConsumptionRepository(
        store: InMemoryPlatformStore
    ): ParticipantCallConsumptionRepository =
        InMemoryParticipantCallConsumptionRepository(store)

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun inMemoryBlobFileClaimStore(): BlobFileClaimStore = InMemoryBlobFileClaimStore()

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "true")
    fun azureTableServiceClient(
        properties: AzureTableProperties,
        azureCredential: AzureCredentialProperties
    ): com.azure.data.tables.TableServiceClient {
        log.info("Persistence: using Azure Table Storage")
        val factory = AzureTableClientFactory(properties, azureCredential)
        val client = factory.createServiceClient()
        factory.ensureTables(client)
        return client
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "true")
    fun azureParticipantRepository(
        service: com.azure.data.tables.TableServiceClient,
        properties: AzureTableProperties
    ): ParticipantRepository =
        AzureTableParticipantRepository(
            service.getTableClient(properties.tableName(properties.participantsTable))
        )

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "true")
    fun azureServiceOfferingRepository(
        service: com.azure.data.tables.TableServiceClient,
        properties: AzureTableProperties
    ): ServiceOfferingRepository =
        AzureTableServiceOfferingRepository(
            service.getTableClient(properties.tableName(properties.servicesTable))
        )

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "true")
    fun azureCallerRepository(
        service: com.azure.data.tables.TableServiceClient,
        properties: AzureTableProperties,
        participants: ParticipantRepository
    ): ParticipantCallerRegistrationRepository =
        AzureTableCallerRegistrationRepository(
            service.getTableClient(properties.tableName(properties.callersTable)),
            participants
        )

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "true")
    fun azureEntitlementRepository(
        service: com.azure.data.tables.TableServiceClient,
        properties: AzureTableProperties,
        participants: ParticipantRepository,
        services: ServiceOfferingRepository
    ): ParticipantServiceEntitlementRepository =
        AzureTableEntitlementRepository(
            service.getTableClient(properties.tableName(properties.entitlementsTable)),
            participants,
            services
        )

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "true")
    fun azureConsumptionRepository(
        service: com.azure.data.tables.TableServiceClient,
        properties: AzureTableProperties,
        callers: ParticipantCallerRegistrationRepository,
        services: ServiceOfferingRepository
    ): ParticipantCallConsumptionRepository =
        AzureTableConsumptionRepository(
            service.getTableClient(properties.tableName(properties.consumptionsTable)),
            service.getTableClient(properties.tableName(properties.consumptionSourceRefTable)),
            callers,
            services
        )

    @Bean
    @ConditionalOnProperty(prefix = "app.azure-table", name = ["enabled"], havingValue = "true")
    fun azureBlobFileClaimStore(
        service: com.azure.data.tables.TableServiceClient,
        properties: AzureTableProperties
    ): BlobFileClaimStore =
        AzureTableBlobFileClaimStore(
            service.getTableClient(properties.tableName(properties.consumptionBlobTable))
        )
}
