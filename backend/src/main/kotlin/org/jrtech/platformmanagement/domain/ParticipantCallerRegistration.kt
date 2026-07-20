package org.jrtech.platformmanagement.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Registration of a caller principal under a [Participant] for billing grouping.
 *
 * [callerId] is the unique key (email, Entra client id, managed identity object id, …)
 * and is unique across the platform — each caller maps to exactly one participant.
 */
@Entity
@Table(name = "participant_caller_registration")
class ParticipantCallerRegistration(

    /**
     * Principal identity value: email, Entra client id (SP / SAMI / UAMI), etc.
     * Primary key — globally unique.
     */
    @field:Id
    @field:Column(name = "caller_id", nullable = false, updatable = false, length = 255)
    var callerId: String,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "participant_id", nullable = false)
    var participant: Participant,

    @field:Convert(converter = CallerRegistrationStatusConverter::class)
    @field:Column(nullable = false, length = 32)
    var status: CallerRegistrationStatus = CallerRegistrationStatus.ACTIVE,

    @field:JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = UtcTimestamps.now(),

    @field:JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = UtcTimestamps.now()
) {
    @PrePersist
    fun onCreate() {
        val now = UtcTimestamps.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = UtcTimestamps.now()
    }
}
