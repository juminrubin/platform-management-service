package com.example.participantapi.dto

import com.example.participantapi.domain.Participant
import com.example.participantapi.domain.ParticipantStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateParticipantRequest(
    @field:NotBlank
    @field:Size(max = 40)
    val id: String,

    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:Size(max = 255)
    val contact: String? = null,

    val status: ParticipantStatus = ParticipantStatus.ACTIVE
)

data class UpdateParticipantRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:Size(max = 255)
    val contact: String? = null,

    val status: ParticipantStatus
)

data class ParticipantResponse(
    val id: String,
    val name: String,
    val contact: String?,
    val status: ParticipantStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(entity: Participant) = ParticipantResponse(
            id = entity.id,
            name = entity.name,
            contact = entity.contact,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
