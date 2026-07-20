package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConsumptionResponse
import org.jrtech.platformmanagement.dto.CreateConsumptionRequest
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.repository.ParticipantCallConsumptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ConsumptionService(
    private val consumptionRepository: ParticipantCallConsumptionRepository,
    private val callerRegistrationService: CallerRegistrationService,
    private val serviceOfferingService: ServiceOfferingService
) {
    private val log = logger()

    @Transactional(readOnly = true)
    fun findAll(
        callerId: String?,
        serviceOfferingId: String?
    ): List<ConsumptionResponse> {
        log.debug(
            "Listing consumption callerId={} serviceOfferingId={}",
            callerId,
            serviceOfferingId
        )
        val entities = when {
            callerId != null ->
                consumptionRepository.findByCallerId(callerId)
            serviceOfferingId != null ->
                consumptionRepository.findByServiceOfferingId(serviceOfferingId)
            else -> consumptionRepository.findAllWithRelations()
        }
        return entities.map(ConsumptionResponse::from)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): ConsumptionResponse {
        log.debug("Fetching consumption id={}", id)
        val entity = consumptionRepository.findByIdWithRelations(id)
            ?: throw ResourceNotFoundException("Consumption record not found: $id")
        return ConsumptionResponse.from(entity)
    }

    @Transactional
    fun create(request: CreateConsumptionRequest): ConsumptionResponse {
        return createFromImport(request, externalId = null).response
    }

    /**
     * Creates a consumption row, optionally with a stable [externalId] for idempotent Avro import.
     * Idempotent when:
     * - [externalId] already exists as the primary key, or
     * - [CreateConsumptionRequest.sourceRefId] already exists (Source Reference Identification).
     */
    @Transactional
    fun createFromImport(
        request: CreateConsumptionRequest,
        externalId: UUID?
    ): ImportCreateResult {
        val callerId = request.callerId.trim()
        val sourceRefId = request.sourceRefId?.trim()?.takeIf { it.isNotEmpty() }

        if (externalId != null && consumptionRepository.existsById(externalId)) {
            log.debug("Skipping import; consumption id={} already exists", externalId)
            return ImportCreateResult(created = false, response = findById(externalId))
        }
        if (sourceRefId != null) {
            val existing = consumptionRepository.findBySourceRefIdWithRelations(sourceRefId)
            if (existing != null) {
                log.debug(
                    "Skipping import; sourceRefId={} already exists as consumption id={}",
                    sourceRefId,
                    existing.id
                )
                return ImportCreateResult(created = false, response = ConsumptionResponse.from(existing))
            }
        }

        log.info(
            "Recording consumption callerId={} serviceOfferingId={} sourceRefId={} consumedAt={} externalId={}",
            callerId,
            request.serviceOfferingId,
            sourceRefId,
            request.consumedAt,
            externalId
        )
        val callerRegistration = callerRegistrationService.getEntity(callerId)
        val offering = serviceOfferingService.getEntity(request.serviceOfferingId.trim())
        val eventTime = request.consumedAt ?: UtcTimestamps.now()

        val saved = consumptionRepository.save(
            ParticipantCallConsumption(
                id = externalId ?: UUID.randomUUID(),
                callerRegistration = callerRegistration,
                serviceOffering = offering,
                sourceRefId = sourceRefId,
                consumptionData = request.consumptionData.trim().ifEmpty { "{}" },
                createdAt = eventTime
            )
        )
        log.info(
            "Created consumption id={} sourceRefId={} createdAt={}",
            saved.id,
            saved.sourceRefId,
            saved.createdAt
        )
        return ImportCreateResult(created = true, response = findById(saved.id))
    }

    data class ImportCreateResult(
        val created: Boolean,
        val response: ConsumptionResponse
    )

    @Transactional
    fun delete(id: UUID) {
        log.info("Deleting consumption id={}", id)
        if (!consumptionRepository.existsById(id)) {
            throw ResourceNotFoundException("Consumption record not found: $id")
        }
        consumptionRepository.deleteById(id)
    }
}
