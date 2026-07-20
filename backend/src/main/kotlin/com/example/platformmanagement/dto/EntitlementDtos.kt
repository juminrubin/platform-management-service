package com.example.platformmanagement.dto

import com.example.platformmanagement.domain.EntitlementStatus
import com.example.platformmanagement.domain.ParticipantServiceEntitlement
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateEntitlementRequest(
    @field:NotBlank
    @field:Size(max = 40)
    val participantId: String,

    @field:NotBlank
    @field:Size(max = 100)
    val serviceOfferingId: String,

    val status: EntitlementStatus = EntitlementStatus.PENDING,

    @field:NotNull
    val validFrom: LocalDate,

    val validTo: LocalDate? = null,

    @field:NotBlank
    @field:Size(max = 5000)
    val config: String = "{}",

    @field:Size(max = 500)
    val notes: String? = null
)

data class UpdateEntitlementRequest(
    val status: EntitlementStatus,

    @field:NotNull
    val validFrom: LocalDate,

    val validTo: LocalDate? = null,

    @field:NotBlank
    @field:Size(max = 5000)
    val config: String,

    @field:Size(max = 500)
    val notes: String? = null
)

data class EntitlementResponse(
    val id: UUID,
    val participantId: String,
    val participantName: String,
    val serviceOfferingId: String,
    val serviceOfferingName: String,
    val status: EntitlementStatus,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val config: String,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(entity: ParticipantServiceEntitlement) = EntitlementResponse(
            id = entity.id,
            participantId = entity.participant.id,
            participantName = entity.participant.name,
            serviceOfferingId = entity.serviceOffering.id,
            serviceOfferingName = entity.serviceOffering.name,
            status = entity.status,
            validFrom = entity.validFrom,
            validTo = entity.validTo,
            config = entity.config,
            notes = entity.notes,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}

/**
 * Result of checking whether a registered caller may use a service offering.
 * Used by [Entitlement.Reader] and [System.Maintainer] roles.
 */
data class EntitlementCheckResponse(
    /** True when an ACTIVE entitlement covers the service for the caller's participant on [asOf]. */
    val allowed: Boolean,
    val reason: String,
    /** Unique principal key of the caller registration. */
    val callerId: String?,
    val participantId: String?,
    val serviceOfferingId: String,
    val asOf: LocalDate,
    val entitlement: EntitlementResponse?
)
