package org.jrtech.platformmanagement.dto

import org.jrtech.platformmanagement.domain.ServiceOffering
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateServiceOfferingRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val id: String,

    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:Size(max = 1000)
    val description: String? = null,

    @field:NotBlank
    @field:Size(max = 100)
    val category: String,

    /**
     * Defaults to SYSTEM when omitted, null, or blank.
     * Nullable so Jackson can omit the property without failing non-null construction.
     */
    @field:Size(max = 100)
    val provider: String? = null,

    @field:NotBlank
    @field:Size(max = 5000)
    val config: String = "{}",

    val active: Boolean = true
)

data class UpdateServiceOfferingRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:Size(max = 1000)
    val description: String? = null,

    @field:NotBlank
    @field:Size(max = 100)
    val category: String,

    @field:NotBlank
    @field:Size(max = 100)
    val provider: String,

    @field:NotBlank
    @field:Size(max = 5000)
    val config: String,

    val active: Boolean
)

data class ServiceOfferingResponse(
    val id: String,
    val name: String,
    val description: String?,
    val category: String,
    val provider: String,
    val config: String,
    val active: Boolean,
    val createdAt: Instant,
    val createdBy: String,
    val updatedAt: Instant,
    val updatedBy: String
) {
    companion object {
        fun from(entity: ServiceOffering) = ServiceOfferingResponse(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            category = entity.category,
            provider = entity.provider,
            config = entity.config,
            active = entity.active,
            createdAt = entity.createdAt,
            createdBy = entity.createdBy,
            updatedAt = entity.updatedAt,
            updatedBy = entity.updatedBy
        )
    }
}
