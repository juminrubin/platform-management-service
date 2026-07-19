package com.example.platformmanagement.controller

import com.example.platformmanagement.domain.CallerIdentityStatus
import com.example.platformmanagement.dto.CallerIdentityResponse
import com.example.platformmanagement.dto.CreateCallerIdentityRequest
import com.example.platformmanagement.dto.UpdateCallerIdentityRequest
import com.example.platformmanagement.service.CallerIdentityService
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Caller identities API.
 * - GET: [AppRoles.SYSTEM_MAINTAINER] or [AppRoles.SYSTEM_READER]
 * - write: [AppRoles.SYSTEM_MAINTAINER]
 */
@RestController
@RequestMapping("/api/v1/caller-identities")
@Tag(name = "Caller identities")
@SecurityRequirement(name = "bearer-jwt")
class CallerIdentityController(
    private val callerIdentityService: CallerIdentityService
) {

    @GetMapping
    @PreAuthorize("@authz.canRead()")
    fun list(
        @RequestParam(required = false) participantId: String?,
        @RequestParam(required = false) status: CallerIdentityStatus?
    ): List<CallerIdentityResponse> = callerIdentityService.findAll(participantId, status)

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canRead()")
    fun get(@PathVariable id: UUID): CallerIdentityResponse =
        callerIdentityService.findById(id)

    @PostMapping
    @PreAuthorize("@authz.canMaintain()")
    fun create(
        @Valid @RequestBody request: CreateCallerIdentityRequest
    ): ResponseEntity<CallerIdentityResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(callerIdentityService.create(request))

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canMaintain()")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCallerIdentityRequest
    ): CallerIdentityResponse = callerIdentityService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authz.canMaintain()")
    fun delete(@PathVariable id: UUID) {
        callerIdentityService.delete(id)
    }
}
