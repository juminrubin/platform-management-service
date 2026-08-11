package org.jrtech.platformmanagement.domain

import java.time.Instant

/**
 * Registration of a caller principal under a [Participant] for billing grouping.
 *
 * [callerId] is the unique key (email, Entra client id, managed identity object id, …).
 */
data class ParticipantCallerRegistration(
    val callerId: String,
    var participant: Participant,
    var createdBy: String,
    var updatedBy: String,
    var status: CallerRegistrationStatus = CallerRegistrationStatus.ACTIVE,
    var createdAt: Instant = UtcTimestamps.now(),
    var updatedAt: Instant = UtcTimestamps.now()
)
