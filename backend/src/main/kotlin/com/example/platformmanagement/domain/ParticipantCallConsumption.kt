package com.example.platformmanagement.domain

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

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "participant_caller_id", nullable = false)
    var participantCallerIdentity: ParticipantCallerIdentity,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "service_offering_id", nullable = false)
    var serviceOffering: ServiceOffering,

    /** JSON usage payload (endpoint_url, input_token, output_token, cache_token, …) — TEXT NOT NULL */
    @field:Column(name = "consumption_data", nullable = false, columnDefinition = "TEXT")
    var consumptionData: String = "{}",

    /**
     * Event time of the consumption (UTC). May be supplied by the registrator;
     * defaults to insert time when not provided. Not overwritten on persist.
     */
    @field:JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = UtcTimestamps.now()
)
