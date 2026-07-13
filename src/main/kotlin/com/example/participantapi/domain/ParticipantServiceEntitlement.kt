package com.example.participantapi.domain

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
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "participant_service_entitlement")
class ParticipantServiceEntitlement(

    @field:Id
    @field:Column(nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "participant_id", nullable = false)
    var participant: Participant,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "service_offering_id", nullable = false)
    var serviceOffering: ServiceOffering,

    @field:Convert(converter = EntitlementStatusConverter::class)
    @field:Column(nullable = false, length = 32)
    var status: EntitlementStatus = EntitlementStatus.PENDING,

    /** Calendar date (no time-of-day / zone). */
    @field:Column(name = "valid_from", nullable = false)
    var validFrom: LocalDate,

    @field:Column(name = "valid_to")
    var validTo: LocalDate? = null,

    /** JSON limits / overrides (max_tpm, max_rpm, …) — VARCHAR(5000) NOT NULL */
    @field:Column(nullable = false, length = 5000)
    var config: String = "{}",

    @field:Column(length = 500)
    var notes: String? = null,

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
