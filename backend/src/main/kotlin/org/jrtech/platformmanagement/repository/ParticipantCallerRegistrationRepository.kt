package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration

interface ParticipantCallerRegistrationRepository {
    fun findByCallerIdWithParticipant(callerId: String): ParticipantCallerRegistration?
    fun findAllWithParticipant(): List<ParticipantCallerRegistration>
    fun findByParticipantId(participantId: String): List<ParticipantCallerRegistration>
    fun findByStatus(status: CallerRegistrationStatus): List<ParticipantCallerRegistration>
    fun existsByCallerId(callerId: String): Boolean
    fun existsById(callerId: String): Boolean
    fun save(entity: ParticipantCallerRegistration): ParticipantCallerRegistration
    fun deleteById(callerId: String)
    fun count(): Long
}
