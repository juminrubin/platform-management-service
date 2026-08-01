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
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "participant_service_entitlement")
class ParticipantServiceEntitlement(

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "participant_id", nullable = false)
    var participant: Participant,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "service_offering_id", nullable = false)
    var serviceOffering: ServiceOffering,

    /** Calendar date (no time-of-day / zone). */
    @field:Column(name = "valid_from", nullable = false)
    var validFrom: LocalDate,

    /**
     * Actor that created the row. Set only by business logic (services);
     * no entity or schema default.
     */
    @field:Column(name = "created_by", nullable = false, updatable = false, length = 255)
    var createdBy: String,

    /**
     * Actor that last updated the row. Set only by business logic on every write.
     */
    @field:Column(name = "updated_by", nullable = false, length = 255)
    var updatedBy: String,

    @field:Id
    @field:Column(nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    @field:Convert(converter = EntitlementStatusConverter::class)
    @field:Column(nullable = false, length = 32)
    var status: EntitlementStatus = EntitlementStatus.PENDING,

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
