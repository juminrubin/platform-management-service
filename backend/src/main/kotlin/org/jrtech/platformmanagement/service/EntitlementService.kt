package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.AuditActors
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.dto.CreateEntitlementRequest
import org.jrtech.platformmanagement.dto.EntitlementCheckResponse
import org.jrtech.platformmanagement.dto.EntitlementResponse
import org.jrtech.platformmanagement.dto.UpdateEntitlementRequest
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class EntitlementService(
    private val entitlementRepository: ParticipantServiceEntitlementRepository,
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val participantService: ParticipantService,
    private val serviceOfferingService: ServiceOfferingService
) {
    private val log = logger()

    @Transactional(readOnly = true)
    fun findAll(
        participantId: String?,
        serviceOfferingId: String?,
        status: EntitlementStatus?
    ): List<EntitlementResponse> {
        log.debug(
            "Listing entitlements participantId={} serviceOfferingId={} status={}",
            participantId,
            serviceOfferingId,
            status
        )
        var entities = when {
            participantId != null -> entitlementRepository.findByParticipantId(participantId)
            serviceOfferingId != null -> entitlementRepository.findByServiceOfferingId(serviceOfferingId)
            else -> entitlementRepository.findAllWithRelations()
        }
        if (status != null) {
            entities = entities.filter { it.status == status }
        }
        log.debug("Found {} entitlement(s)", entities.size)
        return entities.map(EntitlementResponse::from)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): EntitlementResponse {
        log.debug("Fetching entitlement id={}", id)
        val entity = entitlementRepository.findByIdWithRelations(id)
            ?: throw ResourceNotFoundException("Entitlement not found: $id").also {
                log.warn("Entitlement not found id={}", id)
            }
        return EntitlementResponse.from(entity)
    }

    @Transactional
    fun create(request: CreateEntitlementRequest): EntitlementResponse {
        log.info(
            "Creating entitlement participantId={} serviceOfferingId={}",
            request.participantId,
            request.serviceOfferingId
        )
        validateDates(request.validFrom, request.validTo)

        val participant = participantService.getEntity(request.participantId.trim())
        val offering = serviceOfferingService.getEntity(request.serviceOfferingId.trim())

        if (entitlementRepository.existsByParticipantIdAndServiceOfferingId(
                participant.id,
                offering.id
            )
        ) {
            log.warn(
                "Entitlement create conflict participantId={} serviceOfferingId={}",
                participant.id,
                offering.id
            )
            throw ConflictException(
                "Entitlement already exists for participant ${participant.id} " +
                    "and service offering ${offering.id}"
            )
        }

        val saved = entitlementRepository.save(
            ParticipantServiceEntitlement(
                participant = participant,
                serviceOffering = offering,
                status = request.status,
                validFrom = request.validFrom,
                validTo = request.validTo,
                config = request.config.trim().ifEmpty { "{}" },
                notes = request.notes?.trim(),
                createdBy = AuditActors.SYSTEM,
                updatedBy = AuditActors.SYSTEM
            )
        )
        log.info("Created entitlement id={} status={} createdBy={}", saved.id, saved.status, saved.createdBy)
        return findById(saved.id)
    }

    @Transactional
    fun update(id: UUID, request: UpdateEntitlementRequest): EntitlementResponse {
        log.info("Updating entitlement id={}", id)
        validateDates(request.validFrom, request.validTo)
        val entity = entitlementRepository.findByIdWithRelations(id)
            ?: throw ResourceNotFoundException("Entitlement not found: $id").also {
                log.warn("Entitlement not found for update id={}", id)
            }

        entity.status = request.status
        entity.validFrom = request.validFrom
        entity.validTo = request.validTo
        entity.config = request.config.trim().ifEmpty { "{}" }
        entity.notes = request.notes?.trim()
        entity.updatedBy = AuditActors.SYSTEM

        entitlementRepository.save(entity)
        log.info("Updated entitlement id={} status={} updatedBy={}", id, request.status, entity.updatedBy)
        return findById(id)
    }

    @Transactional
    fun delete(id: UUID) {
        log.info("Deleting entitlement id={}", id)
        if (!entitlementRepository.existsById(id)) {
            log.warn("Entitlement not found for delete id={}", id)
            throw ResourceNotFoundException("Entitlement not found: $id")
        }
        entitlementRepository.deleteById(id)
        log.info("Deleted entitlement id={}", id)
    }

    /**
     * Check whether a registered caller is entitled to use a service offering.
     *
     * [callerId] is the unique principal key of the [ParticipantCallerRegistration].
     * Validity is evaluated over the closed UTC date range [[fromDate], [untilDate]]:
     * - [fromDate] defaults to today (UTC) when omitted
     * - [untilDate] defaults to [fromDate] when omitted (point-in-time check)
     *
     * The entitlement must fully cover the requested range (inclusive).
     */
    @Transactional(readOnly = true)
    fun checkByCallerAndService(
        callerId: String,
        serviceOfferingId: String,
        fromDate: LocalDate? = null,
        untilDate: LocalDate? = null
    ): EntitlementCheckResponse {
        val offeringId = serviceOfferingId.trim()
        if (offeringId.isEmpty()) {
            throw BadRequestException("serviceOfferingId is required")
        }
        val resolvedCallerId = callerId.trim()
        if (resolvedCallerId.isEmpty()) {
            throw BadRequestException("callerId is required")
        }

        val resolvedFrom = fromDate ?: LocalDate.now(ZoneOffset.UTC)
        val resolvedUntil = untilDate ?: resolvedFrom
        if (resolvedUntil.isBefore(resolvedFrom)) {
            throw BadRequestException("untilDate must be on or after fromDate")
        }

        // Ensure service offering exists (stable 404 for typos)
        serviceOfferingService.getEntity(offeringId)

        fun deny(
            reason: String,
            callerIdValue: String?,
            participantId: String?,
            entitlement: EntitlementResponse? = null
        ) = EntitlementCheckResponse(
            allowed = false,
            reason = reason,
            callerId = callerIdValue,
            participantId = participantId,
            serviceOfferingId = offeringId,
            fromDate = resolvedFrom,
            untilDate = resolvedUntil,
            entitlement = entitlement
        )

        val caller = callerRegistrationRepository.findByCallerIdWithParticipant(resolvedCallerId)
            ?: return deny(
                reason = "CALLER_NOT_FOUND",
                callerIdValue = resolvedCallerId,
                participantId = null
            )

        if (caller.status != CallerRegistrationStatus.ACTIVE) {
            log.debug(
                "Entitlement check denied: caller inactive callerId={} status={}",
                caller.callerId,
                caller.status
            )
            return deny(
                reason = "CALLER_NOT_ACTIVE",
                callerIdValue = caller.callerId,
                participantId = caller.participant.id
            )
        }

        val entitlement = entitlementRepository
            .findByParticipantId(caller.participant.id)
            .firstOrNull { it.serviceOffering.id == offeringId }

        if (entitlement == null) {
            return deny(
                reason = "NO_ENTITLEMENT",
                callerIdValue = caller.callerId,
                participantId = caller.participant.id
            )
        }

        val response = EntitlementResponse.from(entitlement)
        // Full coverage of [resolvedFrom, resolvedUntil] against entitlement.validFrom/validTo
        val reason = when {
            entitlement.status != EntitlementStatus.ACTIVE -> "ENTITLEMENT_NOT_ACTIVE"
            // Window starts before the entitlement is valid
            resolvedFrom.isBefore(entitlement.validFrom) -> "ENTITLEMENT_NOT_YET_VALID"
            // Window ends after the entitlement expires (open-ended validTo = no expiry)
            entitlement.validTo != null && resolvedUntil.isAfter(entitlement.validTo) ->
                "ENTITLEMENT_EXPIRED"
            else -> "ALLOWED"
        }
        val allowed = reason == "ALLOWED"

        log.debug(
            "Entitlement check callerId={} service={} from={} until={} allowed={} reason={}",
            caller.callerId,
            offeringId,
            resolvedFrom,
            resolvedUntil,
            allowed,
            reason
        )

        return EntitlementCheckResponse(
            allowed = allowed,
            reason = reason,
            callerId = caller.callerId,
            participantId = caller.participant.id,
            serviceOfferingId = offeringId,
            fromDate = resolvedFrom,
            untilDate = resolvedUntil,
            entitlement = response
        )
    }

    private fun validateDates(validFrom: java.time.LocalDate, validTo: java.time.LocalDate?) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            log.warn("Invalid entitlement dates validFrom={} validTo={}", validFrom, validTo)
            throw BadRequestException("validTo must be on or after validFrom")
        }
    }
}
