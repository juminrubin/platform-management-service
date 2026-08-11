package org.jrtech.platformmanagement.bootstrap

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jrtech.platformmanagement.domain.AuditActors
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Loads seed catalog data from [DataSourceLoaderProperties.location] at application startup.
 *
 * Inserts are **idempotent**: existing rows (by primary / unique business key) are left
 * unchanged so restarts and multi-instance boots do not overwrite operational data.
 *
 * Load order: service offerings → participants → caller registrations → entitlements.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.datasource-loader",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(DataSourceLoaderProperties::class)
class DataSourceLoader(
    private val properties: DataSourceLoaderProperties,
    private val resourceLoader: ResourceLoader,
    private val serviceOfferingRepository: ServiceOfferingRepository,
    private val participantRepository: ParticipantRepository,
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val entitlementRepository: ParticipantServiceEntitlementRepository,
    private val objectMapper: ObjectMapper = defaultObjectMapper()
) {
    private val log = logger()

    /** Runs before [org.jrtech.platformmanagement.cache.EntitlementCheckCache] startup load. */
    @EventListener(ApplicationReadyEvent::class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 50)
    @Transactional
    fun loadOnStartup() {
        val location = properties.location.trim().ifEmpty { DEFAULT_LOCATION }
        val resource = resourceLoader.getResource(location)
        if (!resource.exists()) {
            log.warn("Datasource file not found at {}; skipping seed load", location)
            return
        }

        log.info("Loading datasource from {}", location)
        val document = resource.inputStream.use { stream ->
            objectMapper.readValue(stream, DataSourceDocument::class.java)
        }

        val servicesCreated = loadServices(document.services)
        val participantsCreated = loadParticipants(document.participants)
        val callersCreated = loadCallers(document.participants)
        val entitlementsCreated = loadEntitlements(document.participants)

        log.info(
            "Datasource load complete — services +{}, participants +{}, callers +{}, entitlements +{}",
            servicesCreated,
            participantsCreated,
            callersCreated,
            entitlementsCreated
        )
    }

    private fun loadServices(services: List<DataSourceService>): Int {
        var created = 0
        for (item in services) {
            val id = item.id.trim()
            if (id.isEmpty()) {
                log.warn("Skipping service with blank id")
                continue
            }
            if (serviceOfferingRepository.existsById(id)) {
                log.debug("Service offering already exists id={}; skipping", id)
                continue
            }
            serviceOfferingRepository.save(
                ServiceOffering(
                    id = id,
                    name = item.name.trim(),
                    description = item.description?.trim()?.ifEmpty { null },
                    category = item.category.trim(),
                    provider = normalizeProvider(item.provider),
                    config = normalizeConfig(item.config),
                    active = item.active,
                    createdBy = AuditActors.SYSTEM,
                    updatedBy = AuditActors.SYSTEM
                )
            )
            created++
            log.info("Seeded service offering id={}", id)
        }
        return created
    }

    private fun loadParticipants(participants: List<DataSourceParticipant>): Int {
        var created = 0
        for (item in participants) {
            val id = item.id.trim()
            if (id.isEmpty()) {
                log.warn("Skipping participant with blank id")
                continue
            }
            if (participantRepository.existsById(id)) {
                log.debug("Participant already exists id={}; skipping", id)
                continue
            }
            // Name is unique; skip if another row already owns this name.
            if (participantRepository.existsByName(item.name.trim())) {
                log.warn(
                    "Participant name '{}' already exists; skipping seed id={}",
                    item.name.trim(),
                    id
                )
                continue
            }
            participantRepository.save(
                Participant(
                    id = id,
                    name = item.name.trim(),
                    contact = item.contact?.trim()?.ifEmpty { null },
                    status = parseEnum(item.status, ParticipantStatus.ACTIVE),
                    createdBy = AuditActors.SYSTEM,
                    updatedBy = AuditActors.SYSTEM
                )
            )
            created++
            log.info("Seeded participant id={}", id)
        }
        return created
    }

    private fun loadCallers(participants: List<DataSourceParticipant>): Int {
        var created = 0
        for (participantSeed in participants) {
            val participantId = participantSeed.id.trim()
            val participant = participantRepository.findById(participantId).orElse(null)
            if (participant == null) {
                if (participantSeed.callers.isNotEmpty()) {
                    log.warn(
                        "Participant id={} missing; skipping {} caller registration(s)",
                        participantId,
                        participantSeed.callers.size
                    )
                }
                continue
            }
            for (caller in participantSeed.callers) {
                val callerId = caller.id.trim()
                if (callerId.isEmpty()) {
                    log.warn("Skipping caller with blank id under participant {}", participantId)
                    continue
                }
                if (callerRegistrationRepository.existsByCallerId(callerId)) {
                    log.debug("Caller registration already exists callerId={}; skipping", callerId)
                    continue
                }
                callerRegistrationRepository.save(
                    ParticipantCallerRegistration(
                        callerId = callerId,
                        participant = participant,
                        status = parseEnum(caller.status, CallerRegistrationStatus.ACTIVE),
                        createdBy = AuditActors.SYSTEM,
                        updatedBy = AuditActors.SYSTEM
                    )
                )
                created++
                log.info("Seeded caller registration callerId={} participantId={}", callerId, participantId)
            }
        }
        return created
    }

    private fun loadEntitlements(participants: List<DataSourceParticipant>): Int {
        var created = 0
        for (participantSeed in participants) {
            val participantId = participantSeed.id.trim()
            val participant = participantRepository.findById(participantId).orElse(null)
            if (participant == null) {
                if (participantSeed.entitlements.isNotEmpty()) {
                    log.warn(
                        "Participant id={} missing; skipping {} entitlement(s)",
                        participantId,
                        participantSeed.entitlements.size
                    )
                }
                continue
            }
            for (entitlement in participantSeed.entitlements) {
                val serviceId = entitlement.serviceId.trim()
                if (serviceId.isEmpty()) {
                    log.warn("Skipping entitlement with blank serviceId under participant {}", participantId)
                    continue
                }
                if (entitlementRepository.existsByParticipantIdAndServiceOfferingId(participantId, serviceId)) {
                    log.debug(
                        "Entitlement already exists participantId={} serviceId={}; skipping",
                        participantId,
                        serviceId
                    )
                    continue
                }
                val offering = serviceOfferingRepository.findById(serviceId).orElse(null)
                if (offering == null) {
                    log.warn(
                        "Service offering id={} missing; skipping entitlement for participant {}",
                        serviceId,
                        participantId
                    )
                    continue
                }
                entitlementRepository.save(
                    ParticipantServiceEntitlement(
                        participant = participant,
                        serviceOffering = offering,
                        status = parseEnum(entitlement.status, EntitlementStatus.ACTIVE),
                        validFrom = entitlement.validFrom,
                        validTo = entitlement.validTo,
                        config = normalizeConfig(entitlement.config),
                        notes = entitlement.notes?.trim()?.ifEmpty { null },
                        createdBy = AuditActors.SYSTEM,
                        updatedBy = AuditActors.SYSTEM
                    )
                )
                created++
                log.info(
                    "Seeded entitlement participantId={} serviceId={}",
                    participantId,
                    serviceId
                )
            }
        }
        return created
    }

    private fun normalizeConfig(config: String?): String =
        config?.trim()?.ifEmpty { null } ?: "{}"

    private fun normalizeProvider(provider: String?): String =
        provider?.trim()?.uppercase()?.ifEmpty { null } ?: ServiceOffering.DEFAULT_PROVIDER

    private inline fun <reified E : Enum<E>> parseEnum(raw: String?, default: E): E {
        val value = raw?.trim()?.uppercase().orEmpty()
        if (value.isEmpty()) return default
        return try {
            java.lang.Enum.valueOf(E::class.java, value)
        } catch (_: IllegalArgumentException) {
            log.warn("Unknown {} value '{}'; using {}", E::class.simpleName, raw, default)
            default
        }
    }

    companion object {
        const val DEFAULT_LOCATION: String = "classpath:datasource.json"

        fun defaultObjectMapper(): ObjectMapper =
            jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .findAndRegisterModules()
    }
}
