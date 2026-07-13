package com.example.participantapi.controller

import com.example.participantapi.dto.ConsumptionResponse
import com.example.participantapi.dto.CreateConsumptionRequest
import com.example.participantapi.service.ConsumptionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

@RestController
@RequestMapping("/api/v1/consumptions")
class ConsumptionController(
    private val consumptionService: ConsumptionService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) participantCallerIdentityId: UUID?,
        @RequestParam(required = false) serviceOfferingId: String?
    ): List<ConsumptionResponse> =
        consumptionService.findAll(participantCallerIdentityId, serviceOfferingId)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ConsumptionResponse =
        consumptionService.findById(id)

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateConsumptionRequest
    ): ResponseEntity<ConsumptionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(consumptionService.create(request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        consumptionService.delete(id)
    }
}
