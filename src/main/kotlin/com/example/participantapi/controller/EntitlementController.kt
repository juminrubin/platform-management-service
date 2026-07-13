package com.example.participantapi.controller

import com.example.participantapi.domain.EntitlementStatus
import com.example.participantapi.dto.CreateEntitlementRequest
import com.example.participantapi.dto.EntitlementResponse
import com.example.participantapi.dto.UpdateEntitlementRequest
import com.example.participantapi.service.EntitlementService
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
@RequestMapping("/api/v1/entitlements")
class EntitlementController(
    private val entitlementService: EntitlementService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) participantId: String?,
        @RequestParam(required = false) serviceOfferingId: String?,
        @RequestParam(required = false) status: EntitlementStatus?
    ): List<EntitlementResponse> =
        entitlementService.findAll(participantId, serviceOfferingId, status)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): EntitlementResponse =
        entitlementService.findById(id)

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateEntitlementRequest
    ): ResponseEntity<EntitlementResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(entitlementService.create(request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateEntitlementRequest
    ): EntitlementResponse = entitlementService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        entitlementService.delete(id)
    }
}
