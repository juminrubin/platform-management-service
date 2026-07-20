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
        val callerId = request.callerId.trim()
        log.info(
            "Recording consumption callerId={} serviceOfferingId={} consumedAt={}",
            callerId,
            request.serviceOfferingId,
            request.consumedAt
        )
        val callerRegistration = callerRegistrationService.getEntity(callerId)
        val offering = serviceOfferingService.getEntity(request.serviceOfferingId.trim())
        val eventTime = request.consumedAt ?: UtcTimestamps.now()

        val saved = consumptionRepository.save(
            ParticipantCallConsumption(
                callerRegistration = callerRegistration,
                serviceOffering = offering,
                consumptionData = request.consumptionData.trim().ifEmpty { "{}" },
                createdAt = eventTime
            )
        )
        log.info("Created consumption id={} createdAt={}", saved.id, saved.createdAt)
        return findById(saved.id)
    }

    @Transactional
    fun delete(id: UUID) {
        log.info("Deleting consumption id={}", id)
        if (!consumptionRepository.existsById(id)) {
            throw ResourceNotFoundException("Consumption record not found: $id")
        }
        consumptionRepository.deleteById(id)
    }
}
