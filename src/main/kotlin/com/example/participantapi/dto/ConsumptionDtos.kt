package com.example.participantapi.dto

import com.example.participantapi.domain.ParticipantCallConsumption
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateConsumptionRequest(
    @field:NotNull
    val participantCallerIdentityId: UUID,

    @field:NotBlank
    @field:Size(max = 100)
    val serviceOfferingId: String,

    @field:NotBlank
    val consumptionData: String = "{}"
)

data class ConsumptionResponse(
    val id: UUID,
    val participantCallerIdentityId: UUID,
    val callerIdentity: String,
    val participantId: String,
    val participantName: String,
    val serviceOfferingId: String,
    val serviceOfferingName: String,
    val consumptionData: String,
    val createdAt: Instant
) {
    companion object {
        fun from(entity: ParticipantCallConsumption) = ConsumptionResponse(
            id = entity.id,
            participantCallerIdentityId = entity.participantCallerIdentity.id,
            callerIdentity = entity.participantCallerIdentity.callerIdentity,
            participantId = entity.participantCallerIdentity.participant.id,
            participantName = entity.participantCallerIdentity.participant.name,
            serviceOfferingId = entity.serviceOffering.id,
            serviceOfferingName = entity.serviceOffering.name,
            consumptionData = entity.consumptionData,
            createdAt = entity.createdAt
        )
    }
}
