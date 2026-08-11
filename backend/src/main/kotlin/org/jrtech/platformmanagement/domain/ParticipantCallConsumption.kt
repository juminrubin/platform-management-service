package org.jrtech.platformmanagement.domain

import java.time.Instant
import java.util.UUID

/** Consumption record for a caller registration and service offering. */
data class ParticipantCallConsumption(
    var id: UUID = UUID.randomUUID(),
    var callerRegistration: ParticipantCallerRegistration,
    var serviceOffering: ServiceOffering,
    var sourceRefId: String? = null,
    var consumptionData: String = "{}",
    var capturedAt: Instant = UtcTimestamps.now(),
    var createdAt: Instant = UtcTimestamps.now()
)
