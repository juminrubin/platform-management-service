package com.example.platformmanagement.repository

import com.example.platformmanagement.domain.CallerIdentityStatus
import com.example.platformmanagement.domain.ParticipantCallerIdentity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ParticipantCallerIdentityRepository : JpaRepository<ParticipantCallerIdentity, UUID> {

    @Query(
        """
        SELECT c FROM ParticipantCallerIdentity c
        JOIN FETCH c.participant
        WHERE c.id = :id
        """
    )
    fun findByIdWithParticipant(@Param("id") id: UUID): ParticipantCallerIdentity?

    @Query(
        """
        SELECT c FROM ParticipantCallerIdentity c
        JOIN FETCH c.participant
        """
    )
    fun findAllWithParticipant(): List<ParticipantCallerIdentity>

    @Query(
        """
        SELECT c FROM ParticipantCallerIdentity c
        JOIN FETCH c.participant
        WHERE c.participant.id = :participantId
        """
    )
    fun findByParticipantId(@Param("participantId") participantId: String): List<ParticipantCallerIdentity>

    @Query(
        """
        SELECT c FROM ParticipantCallerIdentity c
        JOIN FETCH c.participant
        WHERE c.callerIdentity = :callerIdentity
        """
    )
    fun findByCallerIdentity(
        @Param("callerIdentity") callerIdentity: String
    ): List<ParticipantCallerIdentity>

    fun findByStatus(status: CallerIdentityStatus): List<ParticipantCallerIdentity>

    fun existsByParticipantIdAndCallerIdentity(participantId: String, callerIdentity: String): Boolean
}
