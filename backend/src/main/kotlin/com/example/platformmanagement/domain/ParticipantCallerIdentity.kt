package com.example.platformmanagement.domain

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
import java.util.UUID

@Entity
@Table(name = "participant_caller_id")
class ParticipantCallerIdentity(

    @field:Id
    @field:Column(nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "participant_id", nullable = false)
    var participant: Participant,

    /**
     * Principal identity value: email, Entra client id (SP / SAMI / UAMI), etc.
     * DB column remains `caller_id`.
     */
    @field:Column(name = "caller_id", nullable = false)
    var callerIdentity: String,

    @field:Convert(converter = CallerIdentityStatusConverter::class)
    @field:Column(nullable = false, length = 32)
    var status: CallerIdentityStatus = CallerIdentityStatus.ACTIVE,

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
