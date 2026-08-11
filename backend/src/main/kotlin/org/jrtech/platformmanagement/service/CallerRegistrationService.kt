package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.AuditActors
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.dto.CallerRegistrationResponse
import org.jrtech.platformmanagement.dto.CreateCallerRegistrationRequest
import org.jrtech.platformmanagement.dto.UpdateCallerRegistrationRequest
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.springframework.stereotype.Service

@Service
class CallerRegistrationService(
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val participantService: ParticipantService
) {
    private val log = logger()
    fun findAll(participantId: String?, status: CallerRegistrationStatus?): List<CallerRegistrationResponse> {
        log.debug("Listing caller registrations participantId={} status={}", participantId, status)
        var entities = if (participantId != null) {
            callerRegistrationRepository.findByParticipantId(participantId)
        } else {
            callerRegistrationRepository.findAllWithParticipant()
        }
        if (status != null) {
            entities = entities.filter { it.status == status }
        }
        return entities.map(CallerRegistrationResponse::from)
    }
    fun findByCallerId(callerId: String): CallerRegistrationResponse {
        log.debug("Fetching caller registration callerId={}", callerId)
        val entity = callerRegistrationRepository.findByCallerIdWithParticipant(callerId)
            ?: throw ResourceNotFoundException("Caller registration not found: $callerId")
        return CallerRegistrationResponse.from(entity)
    }
    fun create(request: CreateCallerRegistrationRequest): CallerRegistrationResponse {
        val participantId = request.participantId.trim()
        val callerId = request.callerId.trim()
        log.info("Creating caller registration participantId={} callerId={}", participantId, callerId)

        val participant = participantService.getEntity(participantId)
        if (callerRegistrationRepository.existsByCallerId(callerId)) {
            throw ConflictException(
                "Caller ID '$callerId' is already registered"
            )
        }

        val saved = callerRegistrationRepository.save(
            ParticipantCallerRegistration(
                callerId = callerId,
                participant = participant,
                status = request.status,
                createdBy = AuditActors.SYSTEM,
                updatedBy = AuditActors.SYSTEM
            )
        )
        log.info("Created caller registration callerId={} createdBy={}", saved.callerId, saved.createdBy)
        return findByCallerId(saved.callerId)
    }
    fun update(callerId: String, request: UpdateCallerRegistrationRequest): CallerRegistrationResponse {
        log.info("Updating caller registration callerId={}", callerId)
        val entity = callerRegistrationRepository.findByCallerIdWithParticipant(callerId)
            ?: throw ResourceNotFoundException("Caller registration not found: $callerId")
        entity.status = request.status
        entity.updatedBy = AuditActors.SYSTEM
        callerRegistrationRepository.save(entity)
        return findByCallerId(callerId)
    }
    fun delete(callerId: String) {
        log.info("Deleting caller registration callerId={}", callerId)
        if (!callerRegistrationRepository.existsById(callerId)) {
            throw ResourceNotFoundException("Caller registration not found: $callerId")
        }
        callerRegistrationRepository.deleteById(callerId)
    }

    fun getEntity(callerId: String): ParticipantCallerRegistration =
        callerRegistrationRepository.findByCallerIdWithParticipant(callerId)
            ?: throw ResourceNotFoundException("Caller registration not found: $callerId")
}
