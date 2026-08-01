package org.jrtech.platformmanagement.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "participant_call_consumption")
class ParticipantCallConsumption(

    @field:Id
    @field:Column(nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),

    /** FK to [ParticipantCallerRegistration.callerId]. */
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "caller_id", nullable = false, referencedColumnName = "caller_id")
    var callerRegistration: ParticipantCallerRegistration,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "service_offering_id", nullable = false)
    var serviceOffering: ServiceOffering,

    /**
     * Source Reference Identification — unique key from the consumption reporter
     * (e.g. UUID of the original request to the service). Optional; unique when set.
     */
    @field:Column(name = "source_ref_id", length = 255, unique = true, updatable = false)
    var sourceRefId: String? = null,

    /** JSON usage payload (endpoint_url, input_token, output_token, cache_token, …) — TEXT NOT NULL */
    @field:Column(name = "consumption_data", nullable = false, columnDefinition = "TEXT")
    var consumptionData: String = "{}",

    /**
     * When the consumption was captured at runtime (UTC business event time).
     * Supplied by the registrator / import; defaults to insert time when omitted.
     * Distinct from [createdAt] (when this platform row was stored).
     */
    @field:JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @field:Column(name = "captured_at", nullable = false, updatable = false)
    var capturedAt: Instant = UtcTimestamps.now(),

    /**
     * When this consumption row was inserted into the platform (UTC audit time).
     * Always set at persist; independent of [capturedAt].
     */
    @field:JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = UtcTimestamps.now()
)
