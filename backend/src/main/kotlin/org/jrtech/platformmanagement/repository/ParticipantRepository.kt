package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantStatus

interface ParticipantRepository {
    fun findById(id: String): Participant?
    fun findAll(): List<Participant>
    fun findByStatus(status: ParticipantStatus): List<Participant>
    fun findByName(name: String): Participant?
    fun existsById(id: String): Boolean
    fun existsByName(name: String): Boolean
    fun existsByNameAndIdNot(name: String, id: String): Boolean
    fun save(entity: Participant): Participant
    fun deleteById(id: String)
    fun count(): Long
}
