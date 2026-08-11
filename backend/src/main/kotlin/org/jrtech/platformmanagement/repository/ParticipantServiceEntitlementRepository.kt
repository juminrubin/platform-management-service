package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface ParticipantServiceEntitlementRepository : JpaRepository<ParticipantServiceEntitlement, UUID> {

    @Query(
        """
        SELECT e FROM ParticipantServiceEntitlement e
        JOIN FETCH e.participant
        JOIN FETCH e.serviceOffering
        WHERE e.id = :id
        """
    )
    fun findByIdWithRelations(@Param("id") id: UUID): ParticipantServiceEntitlement?

    @Query(
        """
        SELECT e FROM ParticipantServiceEntitlement e
        JOIN FETCH e.participant
        JOIN FETCH e.serviceOffering
        """
    )
    fun findAllWithRelations(): List<ParticipantServiceEntitlement>

    /**
     * Entitlements that are [EntitlementStatus.ACTIVE] and whose inclusive validity window
     * covers [asOf] (UTC calendar day used by the entitlement check cache).
     *
     * `validFrom <= asOf` and (`validTo` is null or `validTo >= asOf`).
     */
    @Query(
        """
        SELECT e FROM ParticipantServiceEntitlement e
        JOIN FETCH e.participant
        JOIN FETCH e.serviceOffering
        WHERE e.status = :status
          AND e.validFrom <= :asOf
          AND (e.validTo IS NULL OR e.validTo >= :asOf)
        """
    )
    fun findActiveAndValidAsOf(
        @Param("asOf") asOf: LocalDate,
        @Param("status") status: EntitlementStatus
    ): List<ParticipantServiceEntitlement>

    @Query(
        """
        SELECT e FROM ParticipantServiceEntitlement e
        JOIN FETCH e.participant
        JOIN FETCH e.serviceOffering
        WHERE e.participant.id = :participantId
        """
    )
    fun findByParticipantId(@Param("participantId") participantId: String): List<ParticipantServiceEntitlement>

    @Query(
        """
        SELECT e FROM ParticipantServiceEntitlement e
        JOIN FETCH e.participant
        JOIN FETCH e.serviceOffering
        WHERE e.serviceOffering.id = :serviceOfferingId
        """
    )
    fun findByServiceOfferingId(
        @Param("serviceOfferingId") serviceOfferingId: String
    ): List<ParticipantServiceEntitlement>

    fun findByStatus(status: EntitlementStatus): List<ParticipantServiceEntitlement>

    fun existsByParticipantIdAndServiceOfferingId(
        participantId: String,
        serviceOfferingId: String
    ): Boolean
}
