package org.jrtech.platformmanagement.service

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
                notes = request.notes?.trim()
            )
        )
        log.info("Created entitlement id={} status={}", saved.id, saved.status)
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

        entitlementRepository.save(entity)
        log.info("Updated entitlement id={} status={}", id, request.status)
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
     * Validity is evaluated on [asOf] (UTC calendar date; defaults to today UTC).
     */
    @Transactional(readOnly = true)
    fun checkByCallerAndService(
        callerId: String?,
        serviceOfferingId: String,
        asOf: LocalDate?
    ): EntitlementCheckResponse {
        val offeringId = serviceOfferingId.trim()
        if (offeringId.isEmpty()) {
            throw BadRequestException("serviceOfferingId is required")
        }
        val resolvedCallerId = callerId?.trim().orEmpty()
        if (resolvedCallerId.isEmpty()) {
            throw BadRequestException("callerId is required")
        }
        val evaluationDate = asOf ?: LocalDate.now(ZoneOffset.UTC)

        // Ensure service offering exists (stable 404 for typos)
        serviceOfferingService.getEntity(offeringId)

        val caller = callerRegistrationRepository.findByCallerIdWithParticipant(resolvedCallerId)
            ?: return EntitlementCheckResponse(
                allowed = false,
                reason = "CALLER_NOT_FOUND",
                callerId = resolvedCallerId,
                participantId = null,
                serviceOfferingId = offeringId,
                asOf = evaluationDate,
                entitlement = null
            )

        if (caller.status != CallerRegistrationStatus.ACTIVE) {
            log.debug(
                "Entitlement check denied: caller inactive callerId={} status={}",
                caller.callerId,
                caller.status
            )
            return EntitlementCheckResponse(
                allowed = false,
                reason = "CALLER_NOT_ACTIVE",
                callerId = caller.callerId,
                participantId = caller.participant.id,
                serviceOfferingId = offeringId,
                asOf = evaluationDate,
                entitlement = null
            )
        }

        val entitlement = entitlementRepository
            .findByParticipantId(caller.participant.id)
            .firstOrNull { it.serviceOffering.id == offeringId }

        if (entitlement == null) {
            return EntitlementCheckResponse(
                allowed = false,
                reason = "NO_ENTITLEMENT",
                callerId = caller.callerId,
                participantId = caller.participant.id,
                serviceOfferingId = offeringId,
                asOf = evaluationDate,
                entitlement = null
            )
        }

        val response = EntitlementResponse.from(entitlement)
        val reason = when {
            entitlement.status != EntitlementStatus.ACTIVE -> "ENTITLEMENT_NOT_ACTIVE"
            evaluationDate.isBefore(entitlement.validFrom) -> "ENTITLEMENT_NOT_YET_VALID"
            entitlement.validTo != null && evaluationDate.isAfter(entitlement.validTo) ->
                "ENTITLEMENT_EXPIRED"
            else -> "ALLOWED"
        }
        val allowed = reason == "ALLOWED"

        log.debug(
            "Entitlement check callerId={} service={} allowed={} reason={}",
            caller.callerId,
            offeringId,
            allowed,
            reason
        )

        return EntitlementCheckResponse(
            allowed = allowed,
            reason = reason,
            callerId = caller.callerId,
            participantId = caller.participant.id,
            serviceOfferingId = offeringId,
            asOf = evaluationDate,
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
