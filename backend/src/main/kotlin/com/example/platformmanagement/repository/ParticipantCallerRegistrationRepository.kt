package com.example.platformmanagement.repository

import com.example.platformmanagement.domain.CallerRegistrationStatus
import com.example.platformmanagement.domain.ParticipantCallerRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ParticipantCallerRegistrationRepository : JpaRepository<ParticipantCallerRegistration, String> {

    @Query(
        """
        SELECT c FROM ParticipantCallerRegistration c
        JOIN FETCH c.participant
        WHERE c.callerId = :callerId
        """
    )
    fun findByCallerIdWithParticipant(@Param("callerId") callerId: String): ParticipantCallerRegistration?

    @Query(
        """
        SELECT c FROM ParticipantCallerRegistration c
        JOIN FETCH c.participant
        """
    )
    fun findAllWithParticipant(): List<ParticipantCallerRegistration>

    @Query(
        """
        SELECT c FROM ParticipantCallerRegistration c
        JOIN FETCH c.participant
        WHERE c.participant.id = :participantId
        """
    )
    fun findByParticipantId(@Param("participantId") participantId: String): List<ParticipantCallerRegistration>

    fun findByStatus(status: CallerRegistrationStatus): List<ParticipantCallerRegistration>

    fun existsByCallerId(callerId: String): Boolean
}
