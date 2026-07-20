package org.jrtech.platformmanagement.dto

import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateConsumptionRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val callerId: String,

    @field:NotBlank
    @field:Size(max = 100)
    val serviceOfferingId: String,

    /**
     * Source Reference Identification from the consumption reporter
     * (e.g. request UUID). Unique when provided; used for idempotent registration.
     */
    @field:Size(max = 255)
    val sourceRefId: String? = null,

    @field:NotBlank
    val consumptionData: String = "{}",

    /**
     * Optional event time of the consumption (UTC).
     * Defaults to "now" when omitted. Stored as [ConsumptionResponse.createdAt].
     */
    val consumedAt: Instant? = null
)

data class ConsumptionResponse(
    val id: UUID,
    val callerId: String,
    val participantId: String,
    val participantName: String,
    val serviceOfferingId: String,
    val serviceOfferingName: String,
    /** Source Reference Identification supplied by the consumption reporter, if any. */
    val sourceRefId: String?,
    val consumptionData: String,
    val createdAt: Instant
) {
    companion object {
        fun from(entity: ParticipantCallConsumption) = ConsumptionResponse(
            id = entity.id,
            callerId = entity.callerRegistration.callerId,
            participantId = entity.callerRegistration.participant.id,
            participantName = entity.callerRegistration.participant.name,
            serviceOfferingId = entity.serviceOffering.id,
            serviceOfferingName = entity.serviceOffering.name,
            sourceRefId = entity.sourceRefId,
            consumptionData = entity.consumptionData,
            createdAt = entity.createdAt
        )
    }
}
