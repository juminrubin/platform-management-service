package org.jrtech.platformmanagement.persistence.memory

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.persistence.InMemoryPlatformStore
import org.jrtech.platformmanagement.repository.ParticipantCallConsumptionRepository
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import java.time.LocalDate
import java.util.UUID

class InMemoryParticipantRepository(
    private val store: InMemoryPlatformStore
) : ParticipantRepository {
    override fun findById(id: String): Participant? = store.participants[id.trim()]?.copy()
    override fun findAll(): List<Participant> = store.participants.values.map { it.copy() }
    override fun findByStatus(status: ParticipantStatus): List<Participant> =
        store.participants.values.filter { it.status == status }.map { it.copy() }
    override fun findByName(name: String): Participant? =
        store.participants.values.firstOrNull { it.name == name }?.copy()
    override fun existsById(id: String): Boolean = store.participants.containsKey(id.trim())
    override fun existsByName(name: String): Boolean =
        store.participants.values.any { it.name == name }
    override fun existsByNameAndIdNot(name: String, id: String): Boolean =
        store.participants.values.any { it.name == name && it.id != id }
    override fun save(entity: Participant): Participant {
        val now = UtcTimestamps.now()
        val existing = store.participants[entity.id]
        val toSave = entity.copy(
            createdAt = existing?.createdAt ?: entity.createdAt,
            updatedAt = now
        )
        store.participants[entity.id] = toSave
        return toSave.copy()
    }
    override fun deleteById(id: String) {
        store.participants.remove(id.trim())
    }
    override fun count(): Long = store.participants.size.toLong()
}

class InMemoryServiceOfferingRepository(
    private val store: InMemoryPlatformStore
) : ServiceOfferingRepository {
    override fun findById(id: String): ServiceOffering? = store.services[id.trim()]?.copy()
    override fun findAll(): List<ServiceOffering> = store.services.values.map { it.copy() }
    override fun findByActiveTrue(): List<ServiceOffering> =
        store.services.values.filter { it.active }.map { it.copy() }
    override fun findByCategory(category: String): List<ServiceOffering> =
        store.services.values.filter { it.category.equals(category, ignoreCase = true) }.map { it.copy() }
    override fun existsById(id: String): Boolean = store.services.containsKey(id.trim())
    override fun save(entity: ServiceOffering): ServiceOffering {
        val now = UtcTimestamps.now()
        val existing = store.services[entity.id]
        val toSave = entity.copy(
            createdAt = existing?.createdAt ?: entity.createdAt,
            updatedAt = now
        )
        store.services[entity.id] = toSave
        return toSave.copy()
    }
    override fun deleteById(id: String) {
        store.services.remove(id.trim())
    }
    override fun count(): Long = store.services.size.toLong()
}

class InMemoryParticipantCallerRegistrationRepository(
    private val store: InMemoryPlatformStore
) : ParticipantCallerRegistrationRepository {
    override fun findByCallerIdWithParticipant(callerId: String): ParticipantCallerRegistration? {
        val c = store.callers[callerId.trim()] ?: return null
        val p = store.participants[c.participant.id]?.copy() ?: c.participant.copy()
        return c.copy(participant = p)
    }
    override fun findAllWithParticipant(): List<ParticipantCallerRegistration> =
        store.callers.values.map { hydrate(it) }
    override fun findByParticipantId(participantId: String): List<ParticipantCallerRegistration> =
        store.callers.values.filter { it.participant.id == participantId }.map { hydrate(it) }
    override fun findByStatus(status: CallerRegistrationStatus): List<ParticipantCallerRegistration> =
        store.callers.values.filter { it.status == status }.map { hydrate(it) }
    override fun existsByCallerId(callerId: String): Boolean = store.callers.containsKey(callerId.trim())
    override fun existsById(callerId: String): Boolean = existsByCallerId(callerId)
    override fun save(entity: ParticipantCallerRegistration): ParticipantCallerRegistration {
        val now = UtcTimestamps.now()
        val existing = store.callers[entity.callerId]
        val participant = store.participants[entity.participant.id]?.copy()
            ?: entity.participant.copy()
        val toSave = entity.copy(
            participant = participant,
            createdAt = existing?.createdAt ?: entity.createdAt,
            updatedAt = now
        )
        store.callers[entity.callerId] = toSave
        return hydrate(toSave)
    }
    override fun deleteById(callerId: String) {
        store.callers.remove(callerId.trim())
    }
    override fun count(): Long = store.callers.size.toLong()
    private fun hydrate(c: ParticipantCallerRegistration): ParticipantCallerRegistration {
        val p = store.participants[c.participant.id]?.copy() ?: c.participant.copy()
        return c.copy(participant = p)
    }
}

class InMemoryParticipantServiceEntitlementRepository(
    private val store: InMemoryPlatformStore
) : ParticipantServiceEntitlementRepository {
    override fun findByIdWithRelations(id: UUID): ParticipantServiceEntitlement? =
        store.entitlements[id]?.let { hydrate(it) }
    override fun findAllWithRelations(): List<ParticipantServiceEntitlement> =
        store.entitlements.values.map { hydrate(it) }
    override fun findByParticipantId(participantId: String): List<ParticipantServiceEntitlement> =
        store.entitlements.values.filter { it.participant.id == participantId }.map { hydrate(it) }
    override fun findByServiceOfferingId(serviceOfferingId: String): List<ParticipantServiceEntitlement> =
        store.entitlements.values.filter { it.serviceOffering.id == serviceOfferingId }.map { hydrate(it) }
    override fun findByStatus(status: EntitlementStatus): List<ParticipantServiceEntitlement> =
        store.entitlements.values.filter { it.status == status }.map { hydrate(it) }
    override fun findActiveAndValidAsOf(
        asOf: LocalDate,
        status: EntitlementStatus
    ): List<ParticipantServiceEntitlement> =
        store.entitlements.values.filter { e ->
            e.status == status &&
                !e.validFrom.isAfter(asOf) &&
                (e.validTo == null || !e.validTo!!.isBefore(asOf))
        }.map { hydrate(it) }
    override fun existsByParticipantIdAndServiceOfferingId(
        participantId: String,
        serviceOfferingId: String
    ): Boolean =
        store.entitlementByParticipantService.containsKey(
            InMemoryPlatformStore.entitlementKey(participantId, serviceOfferingId)
        )
    override fun existsById(id: UUID): Boolean = store.entitlements.containsKey(id)
    override fun save(entity: ParticipantServiceEntitlement): ParticipantServiceEntitlement {
        val now = UtcTimestamps.now()
        val existing = store.entitlements[entity.id]
        val participant = store.participants[entity.participant.id]?.copy()
            ?: entity.participant.copy()
        val offering = store.services[entity.serviceOffering.id]?.copy()
            ?: entity.serviceOffering.copy()
        val toSave = entity.copy(
            participant = participant,
            serviceOffering = offering,
            createdAt = existing?.createdAt ?: entity.createdAt,
            updatedAt = now
        )
        store.entitlements[toSave.id] = toSave
        store.entitlementByParticipantService[
            InMemoryPlatformStore.entitlementKey(participant.id, offering.id)
        ] = toSave.id
        return hydrate(toSave)
    }
    override fun deleteById(id: UUID) {
        val existing = store.entitlements.remove(id) ?: return
        store.entitlementByParticipantService.remove(
            InMemoryPlatformStore.entitlementKey(existing.participant.id, existing.serviceOffering.id)
        )
    }
    override fun count(): Long = store.entitlements.size.toLong()
    private fun hydrate(e: ParticipantServiceEntitlement): ParticipantServiceEntitlement {
        val p = store.participants[e.participant.id]?.copy() ?: e.participant.copy()
        val s = store.services[e.serviceOffering.id]?.copy() ?: e.serviceOffering.copy()
        return e.copy(participant = p, serviceOffering = s)
    }
}

class InMemoryParticipantCallConsumptionRepository(
    private val store: InMemoryPlatformStore
) : ParticipantCallConsumptionRepository {
    override fun existsBySourceRefId(sourceRefId: String): Boolean =
        store.consumptionBySourceRef.containsKey(sourceRefId.trim())
    override fun existsById(id: UUID): Boolean = store.consumptions.containsKey(id)
    override fun findBySourceRefIdWithRelations(sourceRefId: String): ParticipantCallConsumption? {
        val id = store.consumptionBySourceRef[sourceRefId.trim()] ?: return null
        return findByIdWithRelations(id)
    }
    override fun findByIdWithRelations(id: UUID): ParticipantCallConsumption? =
        store.consumptions[id]?.let { hydrate(it) }
    override fun findAllWithRelations(): List<ParticipantCallConsumption> =
        store.consumptions.values.map { hydrate(it) }
    override fun findByCallerId(callerId: String): List<ParticipantCallConsumption> =
        store.consumptions.values
            .filter { it.callerRegistration.callerId == callerId }
            .map { hydrate(it) }
    override fun findByServiceOfferingId(serviceOfferingId: String): List<ParticipantCallConsumption> =
        store.consumptions.values
            .filter { it.serviceOffering.id == serviceOfferingId }
            .map { hydrate(it) }
    override fun save(entity: ParticipantCallConsumption): ParticipantCallConsumption {
        val caller = store.callers[entity.callerRegistration.callerId]
            ?: entity.callerRegistration
        val participant = store.participants[caller.participant.id]?.copy()
            ?: caller.participant.copy()
        val offering = store.services[entity.serviceOffering.id]?.copy()
            ?: entity.serviceOffering.copy()
        val hydratedCaller = caller.copy(participant = participant)
        val toSave = entity.copy(
            callerRegistration = hydratedCaller,
            serviceOffering = offering
        )
        store.consumptions[toSave.id] = toSave
        toSave.sourceRefId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            store.consumptionBySourceRef[it] = toSave.id
        }
        return hydrate(toSave)
    }
    override fun deleteById(id: UUID) {
        val existing = store.consumptions.remove(id) ?: return
        existing.sourceRefId?.let { store.consumptionBySourceRef.remove(it) }
    }
    override fun count(): Long = store.consumptions.size.toLong()
    private fun hydrate(c: ParticipantCallConsumption): ParticipantCallConsumption {
        val caller = store.callers[c.callerRegistration.callerId]
            ?: c.callerRegistration
        val participant = store.participants[caller.participant.id]?.copy()
            ?: caller.participant.copy()
        val offering = store.services[c.serviceOffering.id]?.copy()
            ?: c.serviceOffering.copy()
        return c.copy(
            callerRegistration = caller.copy(participant = participant),
            serviceOffering = offering
        )
    }
}
