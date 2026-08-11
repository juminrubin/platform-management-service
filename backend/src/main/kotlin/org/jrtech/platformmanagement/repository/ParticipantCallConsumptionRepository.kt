package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import java.util.UUID

interface ParticipantCallConsumptionRepository {
    fun existsBySourceRefId(sourceRefId: String): Boolean
    fun existsById(id: UUID): Boolean
    fun findBySourceRefIdWithRelations(sourceRefId: String): ParticipantCallConsumption?
    fun findByIdWithRelations(id: UUID): ParticipantCallConsumption?
    fun findAllWithRelations(): List<ParticipantCallConsumption>
    fun findByCallerId(callerId: String): List<ParticipantCallConsumption>
    fun findByServiceOfferingId(serviceOfferingId: String): List<ParticipantCallConsumption>
    fun save(entity: ParticipantCallConsumption): ParticipantCallConsumption
    fun deleteById(id: UUID)
    fun count(): Long
}
