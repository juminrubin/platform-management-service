package org.jrtech.platformmanagement.persistence

import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ServiceOffering
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local durable store used when Azure Table is disabled (local/dev/tests).
 * Thread-safe; not shared across instances.
 */
class InMemoryPlatformStore {
    val participants = ConcurrentHashMap<String, Participant>()
    val services = ConcurrentHashMap<String, ServiceOffering>()
    val callers = ConcurrentHashMap<String, ParticipantCallerRegistration>()
    val entitlements = ConcurrentHashMap<UUID, ParticipantServiceEntitlement>()
    /** Secondary index: participantId\0serviceId → entitlement id */
    val entitlementByParticipantService = ConcurrentHashMap<String, UUID>()
    val consumptions = ConcurrentHashMap<UUID, ParticipantCallConsumption>()
    val consumptionBySourceRef = ConcurrentHashMap<String, UUID>()

    fun clear() {
        participants.clear()
        services.clear()
        callers.clear()
        entitlements.clear()
        entitlementByParticipantService.clear()
        consumptions.clear()
        consumptionBySourceRef.clear()
    }

    companion object {
        fun entitlementKey(participantId: String, serviceOfferingId: String): String =
            "${participantId.trim()}\u0000${serviceOfferingId.trim()}"
    }
}
