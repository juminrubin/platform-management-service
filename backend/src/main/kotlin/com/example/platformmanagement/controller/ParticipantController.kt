package com.example.platformmanagement.controller

import com.example.platformmanagement.domain.ParticipantStatus
import com.example.platformmanagement.dto.CreateParticipantRequest
import com.example.platformmanagement.dto.ParticipantResponse
import com.example.platformmanagement.dto.UpdateParticipantRequest
import com.example.platformmanagement.service.ParticipantService
import io.swagger.v3.oas.annotations.Operation
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

/**
 * Participants API.
 * - GET: [AppRoles.SYSTEM_MAINTAINER] or [AppRoles.SYSTEM_READER]
 * - write: [AppRoles.SYSTEM_MAINTAINER]
 */
@RestController
@RequestMapping("/api/v1/participants")
@Tag(name = "Participants")
@SecurityRequirement(name = "bearer-jwt")
class ParticipantController(
    private val participantService: ParticipantService
) {

    @GetMapping
    @PreAuthorize("@authz.canRead()")
    @Operation(summary = "List participants", description = "Requires System.Maintainer or System.Reader.")
    fun list(
        @RequestParam(required = false) status: ParticipantStatus?
    ): List<ParticipantResponse> = participantService.findAll(status)

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canRead()")
    @Operation(summary = "Get participant by id")
    fun get(@PathVariable id: String): ParticipantResponse =
        participantService.findById(id)

    @PostMapping
    @PreAuthorize("@authz.canMaintain()")
    @Operation(summary = "Create participant", description = "Requires System.Maintainer.")
    fun create(
        @Valid @RequestBody request: CreateParticipantRequest
    ): ResponseEntity<ParticipantResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(participantService.create(request))

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(summary = "Update participant")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateParticipantRequest
    ): ParticipantResponse = participantService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authz.canMaintain()")
    @Operation(summary = "Delete participant")
    fun delete(@PathVariable id: String) {
        participantService.delete(id)
    }
}
