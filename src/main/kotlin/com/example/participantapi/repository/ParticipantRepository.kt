package com.example.participantapi.repository

import com.example.participantapi.domain.Participant
import com.example.participantapi.domain.ParticipantStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ParticipantRepository : JpaRepository<Participant, String> {
    fun findByName(name: String): Participant?
    fun findByStatus(status: ParticipantStatus): List<Participant>
    fun existsByName(name: String): Boolean
    fun existsByNameAndIdNot(name: String, id: String): Boolean
}
