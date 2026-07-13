package com.example.participantapi.service

import com.example.participantapi.domain.ServiceOffering
import com.example.participantapi.dto.CreateServiceOfferingRequest
import com.example.participantapi.dto.ServiceOfferingResponse
import com.example.participantapi.dto.UpdateServiceOfferingRequest
import com.example.participantapi.exception.ConflictException
import com.example.participantapi.exception.ResourceNotFoundException
import com.example.participantapi.logging.logger
import com.example.participantapi.repository.ServiceOfferingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServiceOfferingService(
    private val serviceOfferingRepository: ServiceOfferingRepository
) {
    private val log = logger()

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    fun findById(id: String): ServiceOfferingResponse {
        log.debug("Fetching service offering id={}", id)
        return ServiceOfferingResponse.from(getEntity(id))
    }

    @Transactional
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
                config = request.config.trim().ifEmpty { "{}" },
                active = request.active
            )
        )
        log.info("Created service offering id={}", saved.id)
        return ServiceOfferingResponse.from(saved)
    }

    @Transactional
    fun update(id: String, request: UpdateServiceOfferingRequest): ServiceOfferingResponse {
        log.info("Updating service offering id={}", id)
        val entity = getEntity(id)
        entity.name = request.name.trim()
        entity.description = request.description?.trim()
        entity.category = request.category.trim().uppercase()
        entity.config = request.config.trim().ifEmpty { "{}" }
        entity.active = request.active
        val saved = serviceOfferingRepository.save(entity)
        log.info("Updated service offering id={} active={}", saved.id, saved.active)
        return ServiceOfferingResponse.from(saved)
    }

    @Transactional
    fun delete(id: String) {
        log.info("Deleting service offering id={}", id)
        if (!serviceOfferingRepository.existsById(id)) {
            log.warn("Service offering not found for delete id={}", id)
            throw ResourceNotFoundException("Service offering not found: $id")
        }
        serviceOfferingRepository.deleteById(id)
        log.info("Deleted service offering id={}", id)
    }

    fun getEntity(id: String): ServiceOffering =
        serviceOfferingRepository.findById(id).orElseThrow {
            log.warn("Service offering not found id={}", id)
            ResourceNotFoundException("Service offering not found: $id")
        }
}
