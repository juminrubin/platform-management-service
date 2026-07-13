package com.example.participantapi.controller

import com.example.participantapi.domain.ParticipantStatus
import com.example.participantapi.dto.CreateParticipantRequest
import com.example.participantapi.dto.ParticipantResponse
import com.example.participantapi.dto.UpdateParticipantRequest
import com.example.participantapi.service.ParticipantService
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
@RequestMapping("/api/v1/participants")
class ParticipantController(
    private val participantService: ParticipantService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: ParticipantStatus?
    ): List<ParticipantResponse> = participantService.findAll(status)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ParticipantResponse =
        participantService.findById(id)

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateParticipantRequest
    ): ResponseEntity<ParticipantResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(participantService.create(request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateParticipantRequest
    ): ParticipantResponse = participantService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) {
        participantService.delete(id)
    }
}
