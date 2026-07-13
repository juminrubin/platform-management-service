package com.example.participantapi.controller

import com.example.participantapi.dto.CreateServiceOfferingRequest
import com.example.participantapi.dto.ServiceOfferingResponse
import com.example.participantapi.dto.UpdateServiceOfferingRequest
import com.example.participantapi.service.ServiceOfferingService
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

@RestController
@RequestMapping("/api/v1/service-offerings")
class ServiceOfferingController(
    private val serviceOfferingService: ServiceOfferingService
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
        @RequestParam(required = false) category: String?
    ): List<ServiceOfferingResponse> = serviceOfferingService.findAll(activeOnly, category)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ServiceOfferingResponse =
        serviceOfferingService.findById(id)

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateServiceOfferingRequest
    ): ResponseEntity<ServiceOfferingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(serviceOfferingService.create(request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateServiceOfferingRequest
    ): ServiceOfferingResponse = serviceOfferingService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) {
        serviceOfferingService.delete(id)
    }
}
