package org.jrtech.platformmanagement.connectors.consumption.eventhub

import org.jrtech.platformmanagement.connectors.config.ConsumptionEventHubProperties
import org.jrtech.platformmanagement.connectors.consumption.BusinessBodyDecoder
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.service.ConsumptionService
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.messaging.eventhubs.EventProcessorClient
import com.azure.messaging.eventhubs.EventProcessorClientBuilder
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore
import com.azure.messaging.eventhubs.models.ErrorContext
import com.azure.messaging.eventhubs.models.EventContext
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.BlobServiceClientBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Azure Event Processor Client runtime using Managed Identity
 * ([DefaultAzureCredentialBuilder]) and Blob checkpoint store.
 */
class AzureEventHubProcessorRuntime(
    private val properties: ConsumptionEventHubProperties,
    private val bodyDecoder: BusinessBodyDecoder,
    private val consumptionService: ConsumptionService
) : EventHubProcessorRuntime {

    private val log = logger()
    private val clientRef = AtomicReference<EventProcessorClient?>(null)
    private val poisonCounts = ConcurrentHashMap<String, Int>()

    override fun start() {
        synchronized(this) {
            val existing = clientRef.get()
            if (existing != null && existing.isRunning) {
                return
            }
            if (!properties.isConfigured()) {
                throw BadRequestException(
                    "Event Hub connector is not fully configured " +
                        "(namespace, hub name, checkpoint storage URL, checkpoint container)"
                )
            }
            existing?.stop()
            val client = buildClient()
            client.start()
            clientRef.set(client)
            log.info(
                "Azure Event Hub processor started ns={} hub={} consumerGroup={}",
                properties.fullyQualifiedNamespace,
                properties.eventHubName,
                properties.consumerGroup
            )
        }
    }

    override fun stop() {
        synchronized(this) {
            val client = clientRef.getAndSet(null) ?: return
            try {
                client.stop()
                log.info("Azure Event Hub processor stopped")
            } catch (ex: Exception) {
                log.warn("Error stopping Event Hub processor: {}", ex.message)
            }
        }
    }

    override fun isRunning(): Boolean = clientRef.get()?.isRunning == true

    override fun describe(): String =
        "mode=azure ns=${properties.fullyQualifiedNamespace} hub=${properties.eventHubName} " +
            "cg=${properties.consumerGroup} running=${isRunning()}"

    private fun buildClient(): EventProcessorClient {
        val credential = DefaultAzureCredentialBuilder().build()
        val checkpointContainer: BlobContainerAsyncClient =
            BlobServiceClientBuilder()
                .endpoint(properties.checkpointStorageAccountUrl.trim().removeSuffix("/"))
                .credential(credential)
                .buildAsyncClient()
                .getBlobContainerAsyncClient(properties.checkpointContainer.trim())

        // Ensure container exists (ignore race)
        try {
            checkpointContainer.createIfNotExists().block()
        } catch (ex: Exception) {
            log.debug("Checkpoint container ensure: {}", ex.message)
        }

        val checkpointStore = BlobCheckpointStore(checkpointContainer)

        return EventProcessorClientBuilder()
            .credential(credential)
            .fullyQualifiedNamespace(properties.fullyQualifiedNamespace.trim())
            .eventHubName(properties.eventHubName.trim())
            .consumerGroup(properties.consumerGroup.trim().ifEmpty { "\$Default" })
            .checkpointStore(checkpointStore)
            .processEvent { ctx -> onEvent(ctx) }
            .processError { ctx -> onError(ctx) }
            .buildEventProcessorClient()
    }

    private fun onEvent(context: EventContext) {
        val partitionId = context.partitionContext.partitionId
        val body = context.eventData.bodyAsBinaryData.toBytes()
        try {
            val request = bodyDecoder.decodeJson(body, properties.requireSourceRefId)
            val externalId = bodyDecoder.parseExternalIdFromJson(body)
            val result = consumptionService.createFromImport(request, externalId)
            log.debug(
                "EH event processed partition={} created={} sourceRefId={}",
                partitionId,
                result.created,
                result.response.sourceRefId
            )
            poisonCounts.remove(partitionId)
            context.updateCheckpoint()
        } catch (ex: BadRequestException) {
            handlePermanent(partitionId, context, "invalid", ex)
        } catch (ex: ResourceNotFoundException) {
            handlePermanent(partitionId, context, "not_found", ex)
        } catch (ex: Exception) {
            log.error(
                "Transient failure processing EH event partition={}: {}",
                partitionId,
                ex.message,
                ex
            )
            // do not checkpoint — retry
        }
    }

    private fun handlePermanent(
        partitionId: String,
        context: EventContext,
        kind: String,
        ex: Exception
    ) {
        val n = poisonCounts.merge(partitionId, 1, Int::plus) ?: 1
        log.warn(
            "Permanent EH event failure partition={} kind={} count={} detail={}",
            partitionId,
            kind,
            n,
            ex.message
        )
        if (n >= properties.poisonSkipAfter.coerceAtLeast(1)) {
            log.error(
                "Poison skip after {} permanent failures partition={}; checkpointing past event",
                n,
                partitionId
            )
            poisonCounts.remove(partitionId)
            context.updateCheckpoint()
        }
        // else: do not checkpoint so Azure may redeliver; after poisonSkipAfter we skip
    }

    private fun onError(context: ErrorContext) {
        log.error(
            "Event Hub processor error partition={}: {}",
            context.partitionContext?.partitionId,
            context.throwable?.message,
            context.throwable
        )
    }
}
