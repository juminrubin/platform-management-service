package org.jrtech.platformmanagement.dto

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateCallerRegistrationRequest(
    @field:NotBlank
    @field:Size(max = 40)
    val participantId: String,

    @field:NotBlank
    @field:Size(max = 255)
    val callerId: String,

    val status: CallerRegistrationStatus = CallerRegistrationStatus.ACTIVE
)

data class UpdateCallerRegistrationRequest(
    val status: CallerRegistrationStatus
)

data class CallerRegistrationResponse(
    val callerId: String,
    val participantId: String,
    val participantName: String,
    val status: CallerRegistrationStatus,
    val createdAt: Instant,
    val createdBy: String,
    val updatedAt: Instant,
    val updatedBy: String
) {
    companion object {
        fun from(entity: ParticipantCallerRegistration) = CallerRegistrationResponse(
            callerId = entity.callerId,
            participantId = entity.participant.id,
            participantName = entity.participant.name,
            status = entity.status,
            createdAt = entity.createdAt,
            createdBy = entity.createdBy,
            updatedAt = entity.updatedAt,
            updatedBy = entity.updatedBy
        )
    }
}
