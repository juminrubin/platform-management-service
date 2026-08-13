package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.dto.CreateServiceOfferingRequest
import org.jrtech.platformmanagement.dto.ServiceOfferingResponse
import org.jrtech.platformmanagement.dto.UpdateServiceOfferingRequest
import org.jrtech.platformmanagement.service.ServiceOfferingService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Service offerings API.
 * - GET: [AppRoles.SYSTEM_MAINTAINER] or [AppRoles.SYSTEM_READER]
 * - write: [AppRoles.SYSTEM_MAINTAINER]
 */
@RestController
@RequestMapping("/api/v1/service-offerings")
@Tag(name = "Service offerings")
@SecurityRequirement(name = "bearer-jwt")
class ServiceOfferingController(
    private val serviceOfferingService: ServiceOfferingService
) {

    @GetMapping
    @PreAuthorize("@authz.canRead()")
    fun list(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
        @RequestParam(required = false) category: String?
    ): List<ServiceOfferingResponse> = serviceOfferingService.findAll(activeOnly, category)

    @GetMapping("/{*id}")
    @PreAuthorize("@authz.canRead()")
    fun get(@PathVariable id: String): ServiceOfferingResponse =
        serviceOfferingService.findById(PathVariables.fromRemaining(id))

    @PostMapping
    @PreAuthorize("@authz.canMaintain()")
    fun create(
        @Valid @RequestBody request: CreateServiceOfferingRequest
    ): ResponseEntity<ServiceOfferingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(serviceOfferingService.create(request))

    @PutMapping("/{*id}")
    @PreAuthorize("@authz.canMaintain()")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateServiceOfferingRequest
    ): ServiceOfferingResponse = serviceOfferingService.update(PathVariables.fromRemaining(id), request)
}
