package org.jrtech.platformmanagement

import org.jrtech.platformmanagement.cache.EntitlementCheckCache
import org.jrtech.platformmanagement.domain.AuditActors
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import java.time.LocalDate

/**
 * Inserts a small catalog used by security / integration tests that need
 * entitlement-check data without relying on application seed.
 */
object TestCatalogFixtures {

    const val PARTICIPANT_ID = "P001"
    const val CALLER_ID = "sky.walker@company.com"
    const val SERVICE_ID = "gpt-5.1"

    fun ensureMinimalCatalog(
        participants: ParticipantRepository,
        services: ServiceOfferingRepository,
        callers: ParticipantCallerRegistrationRepository,
        entitlements: ParticipantServiceEntitlementRepository,
        cache: EntitlementCheckCache? = null
    ) {
        val offering = services.findById(SERVICE_ID) ?: services.save(
            ServiceOffering(
                id = SERVICE_ID,
                name = "GPT 5.1",
                category = "Language Models",
                provider = "OPENAI",
                config = "{}",
                active = true,
                createdBy = AuditActors.SYSTEM,
                updatedBy = AuditActors.SYSTEM
            )
        )
        val participant = participants.findById(PARTICIPANT_ID) ?: participants.save(
            Participant(
                id = PARTICIPANT_ID,
                name = "Marketing Department",
                contact = CALLER_ID,
                status = ParticipantStatus.ACTIVE,
                createdBy = AuditActors.SYSTEM,
                updatedBy = AuditActors.SYSTEM
            )
        )
        if (!callers.existsByCallerId(CALLER_ID)) {
            callers.save(
                ParticipantCallerRegistration(
                    callerId = CALLER_ID,
                    participant = participant,
                    status = CallerRegistrationStatus.ACTIVE,
                    createdBy = AuditActors.SYSTEM,
                    updatedBy = AuditActors.SYSTEM
                )
            )
        }
        if (!entitlements.existsByParticipantIdAndServiceOfferingId(PARTICIPANT_ID, SERVICE_ID)) {
            entitlements.save(
                ParticipantServiceEntitlement(
                    participant = participant,
                    serviceOffering = offering,
                    status = EntitlementStatus.ACTIVE,
                    validFrom = LocalDate.of(2020, 1, 1),
                    validTo = LocalDate.of(2035, 1, 1),
                    config = "{}",
                    notes = "test",
                    createdBy = AuditActors.SYSTEM,
                    updatedBy = AuditActors.SYSTEM
                )
            )
        }
        cache?.refresh(triggeredBy = "test-fixture")
    }
}
