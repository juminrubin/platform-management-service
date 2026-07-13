package com.example.participantapi.service

import com.example.participantapi.domain.EntitlementStatus
import com.example.participantapi.domain.ParticipantServiceEntitlement
import com.example.participantapi.dto.CreateEntitlementRequest
import com.example.participantapi.dto.EntitlementResponse
import com.example.participantapi.dto.UpdateEntitlementRequest
import com.example.participantapi.exception.BadRequestException
import com.example.participantapi.exception.ConflictException
import com.example.participantapi.exception.ResourceNotFoundException
import com.example.participantapi.logging.logger
import com.example.participantapi.repository.ParticipantServiceEntitlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EntitlementService(
    private val entitlementRepository: ParticipantServiceEntitlementRepository,
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

    private fun validateDates(validFrom: java.time.LocalDate, validTo: java.time.LocalDate?) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            log.warn("Invalid entitlement dates validFrom={} validTo={}", validFrom, validTo)
            throw BadRequestException("validTo must be on or after validFrom")
        }
    }
}
