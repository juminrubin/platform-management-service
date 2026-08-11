package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import java.time.LocalDate
import java.util.UUID

interface ParticipantServiceEntitlementRepository {
    fun findByIdWithRelations(id: UUID): ParticipantServiceEntitlement?
    fun findAllWithRelations(): List<ParticipantServiceEntitlement>
    fun findByParticipantId(participantId: String): List<ParticipantServiceEntitlement>
    fun findByServiceOfferingId(serviceOfferingId: String): List<ParticipantServiceEntitlement>
    fun findByStatus(status: EntitlementStatus): List<ParticipantServiceEntitlement>
    fun findActiveAndValidAsOf(asOf: LocalDate, status: EntitlementStatus): List<ParticipantServiceEntitlement>
    fun existsByParticipantIdAndServiceOfferingId(participantId: String, serviceOfferingId: String): Boolean
    fun existsById(id: UUID): Boolean
    fun save(entity: ParticipantServiceEntitlement): ParticipantServiceEntitlement
    fun deleteById(id: UUID)
    fun count(): Long
}
