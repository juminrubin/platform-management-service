package org.jrtech.platformmanagement.domain

import java.time.Instant

/** Billing group for one or more caller registrations. */
data class Participant(
    val id: String,
    var name: String,
    var createdBy: String,
    var updatedBy: String,
    var contact: String? = null,
    var status: ParticipantStatus = ParticipantStatus.ACTIVE,
    var createdAt: Instant = UtcTimestamps.now(),
    var updatedAt: Instant = UtcTimestamps.now()
)
