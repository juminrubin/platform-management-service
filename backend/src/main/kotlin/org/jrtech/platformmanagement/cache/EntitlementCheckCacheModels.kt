package org.jrtech.platformmanagement.cache

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.dto.EntitlementResponse
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Immutable service-offering projection for existence / display in check responses. */
data class CachedServiceOffering(
    val id: String,
    val name: String,
    val active: Boolean
)

/** Immutable caller registration projection for entitlement checks. */
data class CachedCallerRegistration(
    val callerId: String,
    val participantId: String,
    val participantName: String,
    val status: CallerRegistrationStatus
)

/**
 * Immutable entitlement projection used by the check path.
 * Holds enough fields to build [EntitlementResponse] without a DB hit.
 */
data class CachedEntitlement(
    val id: UUID,
    val participantId: String,
    val participantName: String,
    val serviceOfferingId: String,
    val serviceOfferingName: String,
    val status: EntitlementStatus,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val config: String,
    val notes: String?,
    val createdAt: Instant,
    val createdBy: String,
    val updatedAt: Instant,
    val updatedBy: String
) {
    fun toResponse(): EntitlementResponse =
        EntitlementResponse(
            id = id,
            participantId = participantId,
            participantName = participantName,
            serviceOfferingId = serviceOfferingId,
            serviceOfferingName = serviceOfferingName,
            status = status,
            validFrom = validFrom,
            validTo = validTo,
            config = config,
            notes = notes,
            createdAt = createdAt,
            createdBy = createdBy,
            updatedAt = updatedAt,
            updatedBy = updatedBy
        )
}

/** Thread-safe snapshot swapped atomically on each successful refresh. */
data class EntitlementCheckCacheSnapshot(
    val servicesById: Map<String, CachedServiceOffering> = emptyMap(),
    val callersById: Map<String, CachedCallerRegistration> = emptyMap(),
    /**
     * ACTIVE entitlements whose validity window covers [entitlementsAsOf].
     * Key: [entitlementKey]
     */
    val entitlementsByParticipantAndService: Map<String, CachedEntitlement> = emptyMap(),
    val loadedAt: Instant? = null,
    /** UTC calendar day used when filtering entitlements into this snapshot. */
    val entitlementsAsOf: LocalDate? = null
) {
    companion object {
        fun entitlementKey(participantId: String, serviceOfferingId: String): String =
            "${participantId.trim()}\u0000${serviceOfferingId.trim()}"
    }
}

/** API view of cache health / last refresh. */
data class EntitlementCheckCacheStatusResponse(
    val enabled: Boolean,
    val loaded: Boolean,
    val loadedAt: Instant?,
    /**
     * UTC calendar day used to filter entitlements into the cache
     * (ACTIVE and validity window covering this day only).
     */
    val entitlementsAsOf: LocalDate?,
    val lastRefreshBy: String?,
    val lastRefreshStartedAt: Instant?,
    val lastRefreshFinishedAt: Instant?,
    val lastError: String?,
    val refreshInProgress: Boolean,
    val serviceCount: Int,
    val callerCount: Int,
    /** Count of ACTIVE + currently valid entitlements held in the index. */
    val entitlementCount: Int,
    val scheduledRefreshEnabled: Boolean,
    val refreshIntervalMs: Long
)
