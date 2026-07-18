package com.example.platformmanagement.service

import com.example.platformmanagement.domain.CallerIdentityStatus
import com.example.platformmanagement.domain.EntitlementStatus
import com.example.platformmanagement.domain.ParticipantServiceEntitlement
import com.example.platformmanagement.dto.CreateEntitlementRequest
import com.example.platformmanagement.dto.EntitlementCheckResponse
import com.example.platformmanagement.dto.EntitlementResponse
import com.example.platformmanagement.dto.UpdateEntitlementRequest
import com.example.platformmanagement.exception.BadRequestException
import com.example.platformmanagement.exception.ConflictException
import com.example.platformmanagement.exception.ResourceNotFoundException
import com.example.platformmanagement.logging.logger
import com.example.platformmanagement.repository.ParticipantCallerIdentityRepository
import com.example.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class EntitlementService(
    private val entitlementRepository: ParticipantServiceEntitlementRepository,
    private val callerIdentityRepository: ParticipantCallerIdentityRepository,
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
     * Check whether a caller identity is entitled to use a service offering.
     *
     * Lookup keys (exactly one of [callerIdentity] or [participantCallerIdentityId] required):
     * - [callerIdentity]: principal string (email, Entra client id, managed identity object id, …)
     * - [participantCallerIdentityId]: internal UUID of the caller-identity row
     *
     * Validity is evaluated on [asOf] (UTC calendar date; defaults to today UTC).
     */
    @Transactional(readOnly = true)
    fun checkByCallerAndService(
        callerIdentity: String?,
        participantCallerIdentityId: UUID?,
        serviceOfferingId: String,
        asOf: LocalDate?
    ): EntitlementCheckResponse {
        val offeringId = serviceOfferingId.trim()
        if (offeringId.isEmpty()) {
            throw BadRequestException("serviceOfferingId is required")
        }
        val evaluationDate = asOf ?: LocalDate.now(ZoneOffset.UTC)

        // Ensure service offering exists (stable 404 for typos)
        serviceOfferingService.getEntity(offeringId)

        val caller = resolveCaller(callerIdentity, participantCallerIdentityId)
            ?: return EntitlementCheckResponse(
                allowed = false,
                reason = "CALLER_NOT_FOUND",
                callerIdentity = callerIdentity?.trim()?.ifEmpty { null },
                participantCallerIdentityId = participantCallerIdentityId,
                participantId = null,
                serviceOfferingId = offeringId,
                asOf = evaluationDate,
                entitlement = null
            )

        if (caller.status != CallerIdentityStatus.ACTIVE) {
            log.debug(
                "Entitlement check denied: caller inactive id={} status={}",
                caller.id,
                caller.status
            )
            return EntitlementCheckResponse(
                allowed = false,
                reason = "CALLER_NOT_ACTIVE",
                callerIdentity = caller.callerIdentity,
                participantCallerIdentityId = caller.id,
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
                callerIdentity = caller.callerIdentity,
                participantCallerIdentityId = caller.id,
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
            "Entitlement check caller={} service={} allowed={} reason={}",
            caller.callerIdentity,
            offeringId,
            allowed,
            reason
        )

        return EntitlementCheckResponse(
            allowed = allowed,
            reason = reason,
            callerIdentity = caller.callerIdentity,
            participantCallerIdentityId = caller.id,
            participantId = caller.participant.id,
            serviceOfferingId = offeringId,
            asOf = evaluationDate,
            entitlement = response
        )
    }

    private fun resolveCaller(
        callerIdentity: String?,
        participantCallerIdentityId: UUID?
    ) = when {
        participantCallerIdentityId != null && !callerIdentity.isNullOrBlank() ->
            throw BadRequestException(
                "Provide either callerIdentity or participantCallerIdentityId, not both"
            )
        participantCallerIdentityId != null ->
            callerIdentityRepository.findByIdWithParticipant(participantCallerIdentityId)
        !callerIdentity.isNullOrBlank() -> {
            val matches = callerIdentityRepository.findByCallerIdentity(callerIdentity.trim())
            // Prefer ACTIVE rows when multiple participants share the same principal string
            matches.firstOrNull { it.status == CallerIdentityStatus.ACTIVE }
                ?: matches.firstOrNull()
        }
        else -> throw BadRequestException(
            "Either callerIdentity or participantCallerIdentityId is required"
        )
    }

    private fun validateDates(validFrom: java.time.LocalDate, validTo: java.time.LocalDate?) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            log.warn("Invalid entitlement dates validFrom={} validTo={}", validFrom, validTo)
            throw BadRequestException("validTo must be on or after validFrom")
        }
    }
}
