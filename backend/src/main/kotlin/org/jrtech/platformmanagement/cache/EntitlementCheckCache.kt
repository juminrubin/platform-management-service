package org.jrtech.platformmanagement.cache

import org.jrtech.platformmanagement.domain.AuditActors
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * In-process concurrent index for entitlement checks.
 *
 * Maps (replaced atomically on each successful refresh):
 * - serviceOfferingId → [CachedServiceOffering]
 * - callerId → [CachedCallerRegistration]
 * - (participantId, serviceOfferingId) → [CachedEntitlement]
 *   (only [EntitlementStatus.ACTIVE] rows whose validity covers "today" UTC at refresh)
 *
 * Lifecycle is owned by
 * [org.jrtech.platformmanagement.connectors.datasource.DatasourceLoadingConnector]
 * (start / hourly schedule / stop) and `POST /api/v1/entitlements/cache/refresh`.
 */
@Service
@EnableConfigurationProperties(EntitlementCheckCacheProperties::class)
class EntitlementCheckCache(
    private val properties: EntitlementCheckCacheProperties,
    private val serviceOfferingRepository: ServiceOfferingRepository,
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val entitlementRepository: ParticipantServiceEntitlementRepository
) {
    private val log = logger()

    private val snapshot = AtomicReference(EntitlementCheckCacheSnapshot())
    private val lastRefreshBy = AtomicReference<String?>(null)
    private val lastRefreshStartedAt = AtomicReference<Instant?>(null)
    private val lastRefreshFinishedAt = AtomicReference<Instant?>(null)
    private val lastError = AtomicReference<String?>(null)
    private val refreshInProgress = AtomicBoolean(false)
    private val refreshLock = Any()

    /** True when checks should prefer this index (enabled + at least one successful load). */
    fun isUsableForChecks(): Boolean = properties.enabled && isLoaded()

    fun isLoaded(): Boolean = snapshot.get().loadedAt != null

    fun currentSnapshot(): EntitlementCheckCacheSnapshot = snapshot.get()

    fun findService(serviceOfferingId: String): CachedServiceOffering? =
        snapshot.get().servicesById[serviceOfferingId.trim()]

    fun findCaller(callerId: String): CachedCallerRegistration? =
        snapshot.get().callersById[callerId.trim()]

    fun findEntitlement(participantId: String, serviceOfferingId: String): CachedEntitlement? {
        val key = EntitlementCheckCacheSnapshot.entitlementKey(participantId, serviceOfferingId)
        return snapshot.get().entitlementsByParticipantAndService[key]
    }

    fun status(): EntitlementCheckCacheStatusResponse {
        val snap = snapshot.get()
        return EntitlementCheckCacheStatusResponse(
            enabled = properties.enabled,
            loaded = snap.loadedAt != null,
            loadedAt = snap.loadedAt,
            entitlementsAsOf = snap.entitlementsAsOf,
            lastRefreshBy = lastRefreshBy.get(),
            lastRefreshStartedAt = lastRefreshStartedAt.get(),
            lastRefreshFinishedAt = lastRefreshFinishedAt.get(),
            lastError = lastError.get(),
            refreshInProgress = refreshInProgress.get(),
            serviceCount = snap.servicesById.size,
            callerCount = snap.callersById.size,
            entitlementCount = snap.entitlementsByParticipantAndService.size,
            scheduledRefreshEnabled = properties.scheduledRefreshEnabled,
            refreshIntervalMs = properties.refreshIntervalMs
        )
    }

    /**
     * Full reload from the database. Concurrent calls are serialized; a second call
     * waits for the in-flight reload and then returns the updated snapshot status.
     *
     * Entitlements are limited to **ACTIVE** rows whose inclusive validity window covers
     * today (UTC calendar day) at refresh time — keeps the index small for hourly reloads.
     */
    fun refresh(triggeredBy: String = AuditActors.SYSTEM): EntitlementCheckCacheStatusResponse {
        synchronized(refreshLock) {
            refreshInProgress.set(true)
            lastRefreshStartedAt.set(UtcTimestamps.now())
            lastRefreshBy.set(triggeredBy)
            try {
                val asOf = LocalDate.now(ZoneOffset.UTC)
                val services = serviceOfferingRepository.findAll().associate { s ->
                    s.id to CachedServiceOffering(
                        id = s.id,
                        name = s.name,
                        active = s.active
                    )
                }
                val callers = callerRegistrationRepository.findAllWithParticipant().associate { c ->
                    c.callerId to CachedCallerRegistration(
                        callerId = c.callerId,
                        participantId = c.participant.id,
                        participantName = c.participant.name,
                        status = c.status
                    )
                }
                val entitlements = entitlementRepository
                    .findActiveAndValidAsOf(asOf, EntitlementStatus.ACTIVE)
                    .associate { e ->
                    val key = EntitlementCheckCacheSnapshot.entitlementKey(
                        e.participant.id,
                        e.serviceOffering.id
                    )
                    key to CachedEntitlement(
                        id = e.id,
                        participantId = e.participant.id,
                        participantName = e.participant.name,
                        serviceOfferingId = e.serviceOffering.id,
                        serviceOfferingName = e.serviceOffering.name,
                        status = e.status,
                        validFrom = e.validFrom,
                        validTo = e.validTo,
                        config = e.config,
                        notes = e.notes,
                        createdAt = e.createdAt,
                        createdBy = e.createdBy,
                        updatedAt = e.updatedAt,
                        updatedBy = e.updatedBy
                    )
                }
                val loadedAt = UtcTimestamps.now()
                snapshot.set(
                    EntitlementCheckCacheSnapshot(
                        servicesById = services,
                        callersById = callers,
                        entitlementsByParticipantAndService = entitlements,
                        loadedAt = loadedAt,
                        entitlementsAsOf = asOf
                    )
                )
                lastError.set(null)
                lastRefreshFinishedAt.set(loadedAt)
                log.info(
                    "Entitlement check cache refreshed by={} asOf={} services={} callers={} activeValidEntitlements={}",
                    triggeredBy,
                    asOf,
                    services.size,
                    callers.size,
                    entitlements.size
                )
            } catch (ex: Exception) {
                val message = ex.message ?: ex.javaClass.simpleName
                lastError.set(message)
                lastRefreshFinishedAt.set(UtcTimestamps.now())
                log.error("Entitlement check cache refresh failed by={}: {}", triggeredBy, message, ex)
                throw ex
            } finally {
                refreshInProgress.set(false)
            }
            return status()
        }
    }
}
