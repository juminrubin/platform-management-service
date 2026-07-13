package com.example.participantapi.repository

import com.example.participantapi.domain.ParticipantCallConsumption
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ParticipantCallConsumptionRepository : JpaRepository<ParticipantCallConsumption, UUID> {

    @Query(
        """
        SELECT c FROM ParticipantCallConsumption c
        JOIN FETCH c.participantCallerIdentity caller
        JOIN FETCH caller.participant
        JOIN FETCH c.serviceOffering
        WHERE c.id = :id
        """
    )
    fun findByIdWithRelations(@Param("id") id: UUID): ParticipantCallConsumption?

    @Query(
        """
        SELECT c FROM ParticipantCallConsumption c
        JOIN FETCH c.participantCallerIdentity caller
        JOIN FETCH caller.participant
        JOIN FETCH c.serviceOffering
        """
    )
    fun findAllWithRelations(): List<ParticipantCallConsumption>

    @Query(
        """
        SELECT c FROM ParticipantCallConsumption c
        JOIN FETCH c.participantCallerIdentity caller
        JOIN FETCH caller.participant
        JOIN FETCH c.serviceOffering
        WHERE caller.id = :participantCallerIdentityId
        """
    )
    fun findByParticipantCallerIdentityId(
        @Param("participantCallerIdentityId") participantCallerIdentityId: UUID
    ): List<ParticipantCallConsumption>

    @Query(
        """
        SELECT c FROM ParticipantCallConsumption c
        JOIN FETCH c.participantCallerIdentity caller
        JOIN FETCH caller.participant
        JOIN FETCH c.serviceOffering
        WHERE c.serviceOffering.id = :serviceOfferingId
        """
    )
    fun findByServiceOfferingId(
        @Param("serviceOfferingId") serviceOfferingId: String
    ): List<ParticipantCallConsumption>
}
