package com.example.platformmanagement.domain

import java.time.Instant

/**
 * Central source for audit timestamps. [Instant] is always UTC on the timeline
 * (no local zone offset); pair with Hibernate TIMESTAMP_UTC column mapping.
 */
object UtcTimestamps {
    fun now(): Instant = Instant.now()
}
