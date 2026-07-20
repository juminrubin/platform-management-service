package com.example.platformmanagement.service

import com.example.platformmanagement.domain.CallerRegistrationStatus
import com.example.platformmanagement.domain.ParticipantCallerRegistration
import com.example.platformmanagement.dto.CallerRegistrationResponse
import com.example.platformmanagement.dto.CreateCallerRegistrationRequest
import com.example.platformmanagement.dto.UpdateCallerRegistrationRequest
import com.example.platformmanagement.exception.ConflictException
import com.example.platformmanagement.exception.ResourceNotFoundException
import com.example.platformmanagement.logging.logger
import com.example.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CallerRegistrationService(
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val participantService: ParticipantService
) {
    private val log = logger()

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    fun findByCallerId(callerId: String): CallerRegistrationResponse {
        log.debug("Fetching caller registration callerId={}", callerId)
        val entity = callerRegistrationRepository.findByCallerIdWithParticipant(callerId)
            ?: throw ResourceNotFoundException("Caller registration not found: $callerId")
        return CallerRegistrationResponse.from(entity)
    }

    @Transactional
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
                status = request.status
            )
        )
        log.info("Created caller registration callerId={}", saved.callerId)
        return findByCallerId(saved.callerId)
    }

    @Transactional
    fun update(callerId: String, request: UpdateCallerRegistrationRequest): CallerRegistrationResponse {
        log.info("Updating caller registration callerId={}", callerId)
        val entity = callerRegistrationRepository.findByCallerIdWithParticipant(callerId)
            ?: throw ResourceNotFoundException("Caller registration not found: $callerId")
        entity.status = request.status
        callerRegistrationRepository.save(entity)
        return findByCallerId(callerId)
    }

    @Transactional
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
