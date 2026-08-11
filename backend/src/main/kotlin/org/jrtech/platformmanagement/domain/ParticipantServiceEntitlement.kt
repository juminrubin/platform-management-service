package org.jrtech.platformmanagement.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Entitlement linking a participant to a service offering. */
data class ParticipantServiceEntitlement(
    var participant: Participant,
    var serviceOffering: ServiceOffering,
    var validFrom: LocalDate,
    var createdBy: String,
    var updatedBy: String,
    var id: UUID = UUID.randomUUID(),
    var status: EntitlementStatus = EntitlementStatus.PENDING,
    var validTo: LocalDate? = null,
    var config: String = "{}",
    var notes: String? = null,
    var createdAt: Instant = UtcTimestamps.now(),
    var updatedAt: Instant = UtcTimestamps.now()
)
