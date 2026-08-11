package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.AuditActors
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.dto.CreateServiceOfferingRequest
import org.jrtech.platformmanagement.dto.ServiceOfferingResponse
import org.jrtech.platformmanagement.dto.UpdateServiceOfferingRequest
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.springframework.stereotype.Service

@Service
class ServiceOfferingService(
    private val serviceOfferingRepository: ServiceOfferingRepository
) {
    private val log = logger()
    fun findAll(activeOnly: Boolean, category: String?): List<ServiceOfferingResponse> {
        log.debug("Listing service offerings activeOnly={} category={}", activeOnly, category)
        var entities = if (activeOnly) {
            serviceOfferingRepository.findByActiveTrue()
        } else {
            serviceOfferingRepository.findAll()
        }
        if (category != null) {
            entities = entities.filter { it.category.equals(category, ignoreCase = true) }
        }
        log.debug("Found {} service offering(s)", entities.size)
        return entities.map(ServiceOfferingResponse::from)
    }
    fun findById(id: String): ServiceOfferingResponse {
        log.debug("Fetching service offering id={}", id)
        return ServiceOfferingResponse.from(getEntity(id))
    }
    fun create(request: CreateServiceOfferingRequest): ServiceOfferingResponse {
        val id = request.id.trim()
        log.info("Creating service offering id={}", id)
        if (serviceOfferingRepository.existsById(id)) {
            log.warn("Service offering create conflict on id={}", id)
            throw ConflictException("Service offering with id '$id' already exists")
        }
        val saved = serviceOfferingRepository.save(
            ServiceOffering(
                id = id,
                name = request.name.trim(),
                description = request.description?.trim(),
                category = request.category.trim().uppercase(),
                provider = normalizeProvider(request.provider),
                config = request.config.trim().ifEmpty { "{}" },
                active = request.active,
                createdBy = AuditActors.SYSTEM,
                updatedBy = AuditActors.SYSTEM
            )
        )
        log.info("Created service offering id={} provider={} createdBy={}", saved.id, saved.provider, saved.createdBy)
        return ServiceOfferingResponse.from(saved)
    }
    fun update(id: String, request: UpdateServiceOfferingRequest): ServiceOfferingResponse {
        log.info("Updating service offering id={}", id)
        val entity = getEntity(id)
        entity.name = request.name.trim()
        entity.description = request.description?.trim()
        entity.category = request.category.trim().uppercase()
        entity.provider = normalizeProvider(request.provider)
        entity.config = request.config.trim().ifEmpty { "{}" }
        entity.active = request.active
        entity.updatedBy = AuditActors.SYSTEM
        val saved = serviceOfferingRepository.save(entity)
        log.info(
            "Updated service offering id={} provider={} active={} updatedBy={}",
            saved.id,
            saved.provider,
            saved.active,
            saved.updatedBy
        )
        return ServiceOfferingResponse.from(saved)
    }
    fun delete(id: String) {
        log.info("Deleting service offering id={}", id)
        if (!serviceOfferingRepository.existsById(id)) {
            log.warn("Service offering not found for delete id={}", id)
            throw ResourceNotFoundException("Service offering not found: $id")
        }
        serviceOfferingRepository.deleteById(id)
        log.info("Deleted service offering id={}", id)
    }

    fun getEntity(id: String): ServiceOffering {
        val entity = serviceOfferingRepository.findById(id)
        if (entity == null) {
            log.warn("Service offering not found id={}", id)
            throw ResourceNotFoundException("Service offering not found: $id")
        }
        return entity
    }

    /** Trim, uppercase; null/blank values fall back to [ServiceOffering.DEFAULT_PROVIDER]. */
    private fun normalizeProvider(provider: String?): String =
        provider?.trim()?.uppercase()?.ifEmpty { null } ?: ServiceOffering.DEFAULT_PROVIDER
}
