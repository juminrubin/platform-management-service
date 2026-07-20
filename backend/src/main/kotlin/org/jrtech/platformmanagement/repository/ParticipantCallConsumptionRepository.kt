package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ParticipantCallConsumptionRepository : JpaRepository<ParticipantCallConsumption, UUID> {

    @Query(
        """
        SELECT c FROM ParticipantCallConsumption c
        JOIN FETCH c.callerRegistration caller
        JOIN FETCH caller.participant
        JOIN FETCH c.serviceOffering
        WHERE c.id = :id
        """
    )
    fun findByIdWithRelations(@Param("id") id: UUID): ParticipantCallConsumption?

    @Query(
        """
        SELECT c FROM ParticipantCallConsumption c
        JOIN FETCH c.callerRegistration caller
        JOIN FETCH caller.participant
        JOIN FETCH c.serviceOffering
        """
    )
    fun findAllWithRelations(): List<ParticipantCallConsumption>

    @Query(
        """
        SELECT c FROM ParticipantCallConsumption c
        JOIN FETCH c.callerRegistration caller
        JOIN FETCH caller.participant
        JOIN FETCH c.serviceOffering
        WHERE caller.callerId = :callerId
        """
    )
    fun findByCallerId(
        @Param("callerId") callerId: String
    ): List<ParticipantCallConsumption>

    @Query(
        """
        SELECT c FROM ParticipantCallConsumption c
        JOIN FETCH c.callerRegistration caller
        JOIN FETCH caller.participant
        JOIN FETCH c.serviceOffering
        WHERE c.serviceOffering.id = :serviceOfferingId
        """
    )
    fun findByServiceOfferingId(
        @Param("serviceOfferingId") serviceOfferingId: String
    ): List<ParticipantCallConsumption>
}
