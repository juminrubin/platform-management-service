package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.dto.CallerRegistrationResponse
import org.jrtech.platformmanagement.dto.CreateCallerRegistrationRequest
import org.jrtech.platformmanagement.dto.UpdateCallerRegistrationRequest
import org.jrtech.platformmanagement.service.CallerRegistrationService
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
 * Participant caller registration API.
 * - GET: [AppRoles.SYSTEM_MAINTAINER] or [AppRoles.SYSTEM_READER]
 * - write: [AppRoles.SYSTEM_MAINTAINER]
 *
 * Resource key is [callerId] (unique principal string).
 */
@RestController
@RequestMapping("/api/v1/caller-registrations")
@Tag(name = "Caller registrations")
@SecurityRequirement(name = "bearer-jwt")
class CallerRegistrationController(
    private val callerRegistrationService: CallerRegistrationService
) {

    @GetMapping
    @PreAuthorize("@authz.canRead()")
    fun list(
        @RequestParam(required = false) participantId: String?,
        @RequestParam(required = false) status: CallerRegistrationStatus?
    ): List<CallerRegistrationResponse> = callerRegistrationService.findAll(participantId, status)

    @GetMapping("/{callerId}")
    @PreAuthorize("@authz.canRead()")
    fun get(@PathVariable callerId: String): CallerRegistrationResponse =
        callerRegistrationService.findByCallerId(callerId)

    @PostMapping
    @PreAuthorize("@authz.canMaintain()")
    fun create(
        @Valid @RequestBody request: CreateCallerRegistrationRequest
    ): ResponseEntity<CallerRegistrationResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(callerRegistrationService.create(request))

    @PutMapping("/{callerId}")
    @PreAuthorize("@authz.canMaintain()")
    fun update(
        @PathVariable callerId: String,
        @Valid @RequestBody request: UpdateCallerRegistrationRequest
    ): CallerRegistrationResponse = callerRegistrationService.update(callerId, request)
}
