package com.example.participantapi.controller

import com.example.participantapi.domain.CallerIdentityStatus
import com.example.participantapi.dto.CallerIdentityResponse
import com.example.participantapi.dto.CreateCallerIdentityRequest
import com.example.participantapi.dto.UpdateCallerIdentityRequest
import com.example.participantapi.service.CallerIdentityService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

@RestController
@RequestMapping("/api/v1/caller-identities")
class CallerIdentityController(
    private val callerIdentityService: CallerIdentityService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) participantId: String?,
        @RequestParam(required = false) status: CallerIdentityStatus?
    ): List<CallerIdentityResponse> = callerIdentityService.findAll(participantId, status)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): CallerIdentityResponse =
        callerIdentityService.findById(id)

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateCallerIdentityRequest
    ): ResponseEntity<CallerIdentityResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(callerIdentityService.create(request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCallerIdentityRequest
    ): CallerIdentityResponse = callerIdentityService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        callerIdentityService.delete(id)
    }
}
