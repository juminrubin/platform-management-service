package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.dto.CreateEntitlementRequest
import org.jrtech.platformmanagement.dto.EntitlementCheckResponse
import org.jrtech.platformmanagement.dto.EntitlementResponse
import org.jrtech.platformmanagement.dto.UpdateEntitlementRequest
import org.jrtech.platformmanagement.service.EntitlementService
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
import java.time.LocalDate
import java.util.UUID

/**
 * Entitlement API.
 * - GET list/get: [AppRoles.SYSTEM_MAINTAINER] or [AppRoles.SYSTEM_READER]
 * - GET check: maintainer, system reader, or [AppRoles.ENTITLEMENT_READER]
 * - write: [AppRoles.SYSTEM_MAINTAINER]
 */
@RestController
@RequestMapping("/api/v1/entitlements")
@Tag(name = "Entitlements")
@SecurityRequirement(name = "bearer-jwt")
class EntitlementController(
    private val entitlementService: EntitlementService
) {

    /**
     * Entitlement check for a registered caller against a service offering.
     * Query params:
     * - `callerId` (required) — unique principal of the caller registration
     * - `serviceOfferingId` (required)
     * - `fromDate` optional ISO date (UTC calendar day; defaults to today UTC)
     * - `untilDate` optional ISO date (UTC calendar day; defaults to [fromDate])
     *
     * The entitlement must fully cover the closed range [[fromDate], [untilDate]].
     */
    @GetMapping("/check")
    @PreAuthorize("@authz.canCheckEntitlement()")
    @Operation(
        summary = "Check entitlement for a caller registration",
        description = "Requires System.Maintainer, System.Reader, or Entitlement.Reader. " +
            "Provide callerId (unique principal of the registration). " +
            "Optional fromDate/untilDate (UTC calendar days) default to today; " +
            "entitlement must cover the full inclusive range."
    )
    fun check(
        @RequestParam callerId: String,
        @RequestParam serviceOfferingId: String,
        @RequestParam(required = false) fromDate: LocalDate?,
        @RequestParam(required = false) untilDate: LocalDate?
    ): EntitlementCheckResponse =
        entitlementService.checkByCallerAndService(
            callerId = callerId,
            serviceOfferingId = serviceOfferingId,
            fromDate = fromDate,
            untilDate = untilDate
        )

    @GetMapping
    @PreAuthorize("@authz.canRead()")
    fun list(
        @RequestParam(required = false) participantId: String?,
        @RequestParam(required = false) serviceOfferingId: String?,
        @RequestParam(required = false) status: EntitlementStatus?
    ): List<EntitlementResponse> =
        entitlementService.findAll(participantId, serviceOfferingId, status)

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canRead()")
    fun get(@PathVariable id: UUID): EntitlementResponse =
        entitlementService.findById(id)

    @PostMapping
    @PreAuthorize("@authz.canMaintain()")
    fun create(
        @Valid @RequestBody request: CreateEntitlementRequest
    ): ResponseEntity<EntitlementResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(entitlementService.create(request))

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canMaintain()")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateEntitlementRequest
    ): EntitlementResponse = entitlementService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authz.canMaintain()")
    fun delete(@PathVariable id: UUID) {
        entitlementService.delete(id)
    }
}
