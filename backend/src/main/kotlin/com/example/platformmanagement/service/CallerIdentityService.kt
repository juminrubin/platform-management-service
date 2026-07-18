package com.example.platformmanagement.service

import com.example.platformmanagement.domain.CallerIdentityStatus
import com.example.platformmanagement.domain.ParticipantCallerIdentity
import com.example.platformmanagement.dto.CallerIdentityResponse
import com.example.platformmanagement.dto.CreateCallerIdentityRequest
import com.example.platformmanagement.dto.UpdateCallerIdentityRequest
import com.example.platformmanagement.exception.ConflictException
import com.example.platformmanagement.exception.ResourceNotFoundException
import com.example.platformmanagement.logging.logger
import com.example.platformmanagement.repository.ParticipantCallerIdentityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CallerIdentityService(
    private val callerIdentityRepository: ParticipantCallerIdentityRepository,
    private val participantService: ParticipantService
) {
    private val log = logger()

    @Transactional(readOnly = true)
    fun findAll(participantId: String?, status: CallerIdentityStatus?): List<CallerIdentityResponse> {
        log.debug("Listing caller identities participantId={} status={}", participantId, status)
        var entities = if (participantId != null) {
            callerIdentityRepository.findByParticipantId(participantId)
        } else {
            callerIdentityRepository.findAllWithParticipant()
        }
        if (status != null) {
            entities = entities.filter { it.status == status }
        }
        return entities.map(CallerIdentityResponse::from)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): CallerIdentityResponse {
        log.debug("Fetching caller identity record id={}", id)
        val entity = callerIdentityRepository.findByIdWithParticipant(id)
            ?: throw ResourceNotFoundException("Caller identity not found: $id")
        return CallerIdentityResponse.from(entity)
    }

    @Transactional
    fun create(request: CreateCallerIdentityRequest): CallerIdentityResponse {
        val participantId = request.participantId.trim()
        val callerIdentity = request.callerIdentity.trim()
        log.info("Creating caller identity participantId={} callerIdentity={}", participantId, callerIdentity)

        val participant = participantService.getEntity(participantId)
        if (callerIdentityRepository.existsByParticipantIdAndCallerIdentity(participantId, callerIdentity)) {
            throw ConflictException(
                "Caller identity '$callerIdentity' already registered for participant '$participantId'"
            )
        }

        val saved = callerIdentityRepository.save(
            ParticipantCallerIdentity(
                participant = participant,
                callerIdentity = callerIdentity,
                status = request.status
            )
        )
        log.info("Created caller identity record id={}", saved.id)
        return findById(saved.id)
    }

    @Transactional
    fun update(id: UUID, request: UpdateCallerIdentityRequest): CallerIdentityResponse {
        log.info("Updating caller identity record id={}", id)
        val entity = callerIdentityRepository.findByIdWithParticipant(id)
            ?: throw ResourceNotFoundException("Caller identity not found: $id")
        entity.status = request.status
        callerIdentityRepository.save(entity)
        return findById(id)
    }

    @Transactional
    fun delete(id: UUID) {
        log.info("Deleting caller identity record id={}", id)
        if (!callerIdentityRepository.existsById(id)) {
            throw ResourceNotFoundException("Caller identity not found: $id")
        }
        callerIdentityRepository.deleteById(id)
    }

    fun getEntity(id: UUID): ParticipantCallerIdentity =
        callerIdentityRepository.findByIdWithParticipant(id)
            ?: throw ResourceNotFoundException("Caller identity not found: $id")
}
