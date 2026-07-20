package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.dto.CreateParticipantRequest
import org.jrtech.platformmanagement.dto.ParticipantResponse
import org.jrtech.platformmanagement.dto.UpdateParticipantRequest
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ParticipantService(
    private val participantRepository: ParticipantRepository
) {
    private val log = logger()

    @Transactional(readOnly = true)
    fun findAll(status: ParticipantStatus?): List<ParticipantResponse> {
        log.debug("Listing participants status={}", status)
        val entities = if (status != null) {
            participantRepository.findByStatus(status)
        } else {
            participantRepository.findAll()
        }
        log.debug("Found {} participant(s)", entities.size)
        return entities.map(ParticipantResponse::from)
    }

    @Transactional(readOnly = true)
    fun findById(id: String): ParticipantResponse {
        log.debug("Fetching participant id={}", id)
        return ParticipantResponse.from(getEntity(id))
    }

    @Transactional
    fun create(request: CreateParticipantRequest): ParticipantResponse {
        val id = request.id.trim()
        log.info("Creating participant id={}", id)
        if (participantRepository.existsById(id)) {
            log.warn("Participant create conflict on id={}", id)
            throw ConflictException("Participant with id '$id' already exists")
        }
        val name = request.name.trim()
        if (participantRepository.existsByName(name)) {
            log.warn("Participant create conflict on name={}", name)
            throw ConflictException("Participant with name '$name' already exists")
        }
        val saved = participantRepository.save(
            Participant(
                id = id,
                name = name,
                contact = request.contact?.trim(),
                status = request.status
            )
        )
        log.info("Created participant id={}", saved.id)
        return ParticipantResponse.from(saved)
    }

    @Transactional
    fun update(id: String, request: UpdateParticipantRequest): ParticipantResponse {
        log.info("Updating participant id={}", id)
        val entity = getEntity(id)
        val name = request.name.trim()
        if (participantRepository.existsByNameAndIdNot(name, id)) {
            log.warn("Participant update conflict on name={} id={}", name, id)
            throw ConflictException("Participant with name '$name' already exists")
        }
        entity.name = name
        entity.contact = request.contact?.trim()
        entity.status = request.status
        val saved = participantRepository.save(entity)
        log.info("Updated participant id={} status={}", saved.id, saved.status)
        return ParticipantResponse.from(saved)
    }

    @Transactional
    fun delete(id: String) {
        log.info("Deleting participant id={}", id)
        if (!participantRepository.existsById(id)) {
            log.warn("Participant not found for delete id={}", id)
            throw ResourceNotFoundException("Participant not found: $id")
        }
        participantRepository.deleteById(id)
        log.info("Deleted participant id={}", id)
    }

    fun getEntity(id: String): Participant =
        participantRepository.findById(id).orElseThrow {
            log.warn("Participant not found id={}", id)
            ResourceNotFoundException("Participant not found: $id")
        }
}
