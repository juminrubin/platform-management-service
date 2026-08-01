package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConsumptionResponse
import org.jrtech.platformmanagement.dto.CreateConsumptionRequest
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.repository.ParticipantCallConsumptionRepository
import org.springframework.dao.DataIntegrityViolationException
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
     * Creates a consumption row, optionally with a stable [externalId] for idempotent import.
     *
     * Idempotent when:
     * - [externalId] already exists as the primary key, or
     * - [CreateConsumptionRequest.sourceRefId] already exists (Source Reference Identification).
     *
     * Race-safe: concurrent inserts that hit unique constraints (PK / source_ref_id /
     * caller+offering+captured_at) re-read the existing row and return [ImportCreateResult.created]=false
     * instead of failing the caller.
     */
    @Transactional
    fun createFromImport(
        request: CreateConsumptionRequest,
        externalId: UUID?
    ): ImportCreateResult {
        val callerId = request.callerId.trim()
        val sourceRefId = request.sourceRefId?.trim()?.takeIf { it.isNotEmpty() }

        findExistingDuplicate(externalId, sourceRefId)?.let { return it }

        log.info(
            "Recording consumption callerId={} serviceOfferingId={} sourceRefId={} capturedAt={} externalId={}",
            callerId,
            request.serviceOfferingId,
            sourceRefId,
            request.capturedAt,
            externalId
        )
        val callerRegistration = callerRegistrationService.getEntity(callerId)
        val offering = serviceOfferingService.getEntity(request.serviceOfferingId.trim())
        val now = UtcTimestamps.now()
        val capturedAt = request.capturedAt ?: now
        val id = externalId ?: UUID.randomUUID()

        return try {
            val saved = consumptionRepository.saveAndFlush(
                ParticipantCallConsumption(
                    id = id,
                    callerRegistration = callerRegistration,
                    serviceOffering = offering,
                    sourceRefId = sourceRefId,
                    consumptionData = request.consumptionData.trim().ifEmpty { "{}" },
                    capturedAt = capturedAt,
                    createdAt = now
                )
            )
            log.info(
                "Created consumption id={} sourceRefId={} capturedAt={} createdAt={}",
                saved.id,
                saved.sourceRefId,
                saved.capturedAt,
                saved.createdAt
            )
            ImportCreateResult(created = true, response = findById(saved.id))
        } catch (ex: DataIntegrityViolationException) {
            log.info(
                "Consumption insert raced on unique constraint; treating as duplicate " +
                    "sourceRefId={} externalId={} cause={}",
                sourceRefId,
                externalId,
                ex.mostSpecificCause.message
            )
            findExistingDuplicate(externalId, sourceRefId)
                ?: throw ex
        }
    }

    private fun findExistingDuplicate(
        externalId: UUID?,
        sourceRefId: String?
    ): ImportCreateResult? {
        if (externalId != null) {
            val byId = consumptionRepository.findByIdWithRelations(externalId)
            if (byId != null) {
                log.debug("Skipping import; consumption id={} already exists", externalId)
                return ImportCreateResult(created = false, response = ConsumptionResponse.from(byId))
            }
        }
        if (sourceRefId != null) {
            val byRef = consumptionRepository.findBySourceRefIdWithRelations(sourceRefId)
            if (byRef != null) {
                log.debug(
                    "Skipping import; sourceRefId={} already exists as consumption id={}",
                    sourceRefId,
                    byRef.id
                )
                return ImportCreateResult(created = false, response = ConsumptionResponse.from(byRef))
            }
        }
        return null
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
