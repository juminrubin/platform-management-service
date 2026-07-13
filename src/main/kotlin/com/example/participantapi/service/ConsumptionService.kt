package com.example.participantapi.service

import com.example.participantapi.domain.ParticipantCallConsumption
import com.example.participantapi.dto.ConsumptionResponse
import com.example.participantapi.dto.CreateConsumptionRequest
import com.example.participantapi.exception.ResourceNotFoundException
import com.example.participantapi.logging.logger
import com.example.participantapi.repository.ParticipantCallConsumptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ConsumptionService(
    private val consumptionRepository: ParticipantCallConsumptionRepository,
    private val callerIdentityService: CallerIdentityService,
    private val serviceOfferingService: ServiceOfferingService
) {
    private val log = logger()

    @Transactional(readOnly = true)
    fun findAll(
        participantCallerIdentityId: UUID?,
        serviceOfferingId: String?
    ): List<ConsumptionResponse> {
        log.debug(
            "Listing consumption participantCallerIdentityId={} serviceOfferingId={}",
            participantCallerIdentityId,
            serviceOfferingId
        )
        val entities = when {
            participantCallerIdentityId != null ->
                consumptionRepository.findByParticipantCallerIdentityId(participantCallerIdentityId)
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
        log.info(
            "Recording consumption participantCallerIdentityId={} serviceOfferingId={}",
            request.participantCallerIdentityId,
            request.serviceOfferingId
        )
        val callerIdentity = callerIdentityService.getEntity(request.participantCallerIdentityId)
        val offering = serviceOfferingService.getEntity(request.serviceOfferingId.trim())

        val saved = consumptionRepository.save(
            ParticipantCallConsumption(
                participantCallerIdentity = callerIdentity,
                serviceOffering = offering,
                consumptionData = request.consumptionData.trim().ifEmpty { "{}" }
            )
        )
        log.info("Created consumption id={}", saved.id)
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
