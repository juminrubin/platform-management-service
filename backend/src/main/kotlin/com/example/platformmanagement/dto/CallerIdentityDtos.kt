package com.example.platformmanagement.dto

import com.example.platformmanagement.domain.CallerIdentityStatus
import com.example.platformmanagement.domain.ParticipantCallerIdentity
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateCallerIdentityRequest(
    @field:NotBlank
    @field:Size(max = 40)
    val participantId: String,

    @field:NotBlank
    @field:Size(max = 255)
    val callerIdentity: String,

    val status: CallerIdentityStatus = CallerIdentityStatus.ACTIVE
)

data class UpdateCallerIdentityRequest(
    val status: CallerIdentityStatus
)

data class CallerIdentityResponse(
    val id: UUID,
    val participantId: String,
    val participantName: String,
    val callerIdentity: String,
    val status: CallerIdentityStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(entity: ParticipantCallerIdentity) = CallerIdentityResponse(
            id = entity.id,
            participantId = entity.participant.id,
            participantName = entity.participant.name,
            callerIdentity = entity.callerIdentity,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
