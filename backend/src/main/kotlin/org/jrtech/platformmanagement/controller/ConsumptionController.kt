package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.dto.ConsumptionResponse
import org.jrtech.platformmanagement.dto.CreateConsumptionRequest
import org.jrtech.platformmanagement.service.ConsumptionService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Consumption API.
 * - GET: [AppRoles.SYSTEM_MAINTAINER] or [AppRoles.SYSTEM_READER]
 * - POST: [AppRoles.SYSTEM_MAINTAINER] or [AppRoles.CONSUMPTION_REGISTRATOR]
 * - DELETE: [AppRoles.SYSTEM_MAINTAINER]
 */
@RestController
@RequestMapping("/api/v1/consumptions")
@Tag(name = "Consumptions")
@SecurityRequirement(name = "bearer-jwt")
class ConsumptionController(
    private val consumptionService: ConsumptionService
) {

    @GetMapping
    @PreAuthorize("@authz.canRead()")
    fun list(
        @RequestParam(required = false) callerId: String?,
        @RequestParam(required = false) serviceOfferingId: String?
    ): List<ConsumptionResponse> =
        consumptionService.findAll(callerId, serviceOfferingId)

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canRead()")
    fun get(@PathVariable id: UUID): ConsumptionResponse =
        consumptionService.findById(id)

    /**
     * Register token consumption for a caller registration against a service offering.
     * Intended for service principals / managed identities with
     * [AppRoles.CONSUMPTION_REGISTRATOR].
     */
    @PostMapping
    @PreAuthorize("@authz.canRegisterConsumption()")
    fun create(
        @Valid @RequestBody request: CreateConsumptionRequest
    ): ResponseEntity<ConsumptionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(consumptionService.create(request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authz.canMaintain()")
    fun delete(@PathVariable id: UUID) {
        consumptionService.delete(id)
    }
}
