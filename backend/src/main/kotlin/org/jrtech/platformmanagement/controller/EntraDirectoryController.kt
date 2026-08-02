package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.dto.EntraDirectorySnapshotResponse
import org.jrtech.platformmanagement.dto.EntraGroupMemberResponse
import org.jrtech.platformmanagement.entra.EntraGroupDirectoryService
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.service.AuditPrincipalResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Data plane for Entra Platform-System-* groups and members (loaded by the
 * **entra-directory** connector process).
 *
 * Control plane (runtime info, config, start/stop, ≤32KB log snapshot):
 * `/api/v1/connectors/entra-directory`.
 *
 * All endpoints require [org.jrtech.platformmanagement.security.AppRoles.SYSTEM_MAINTAINER].
 */
@RestController
@RequestMapping("/api/v1/entra")
@Tag(name = "Entra directory")
@SecurityRequirement(name = "bearer-jwt")
class EntraDirectoryController(
    private val entraGroupDirectoryService: EntraGroupDirectoryService,
    private val auditPrincipalResolver: AuditPrincipalResolver
) {

    @GetMapping("/groups")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "List Platform-System-* Entra groups and members",
        description = "Returns the cached snapshot of Entra groups whose display name " +
            "starts with the configured prefix (default Platform-System-) and each group's members. " +
            "Requires System.Maintainer. Enable with app.entra-directory.enabled=true."
    )
    fun listGroups(): EntraDirectorySnapshotResponse =
        entraGroupDirectoryService.snapshotResponse()

    @GetMapping("/members")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "List unique members across Platform-System-* groups",
        description = "Flattens members from the cached group snapshot (deduplicated by object id). " +
            "Requires System.Maintainer."
    )
    fun listMembers(): List<EntraGroupMemberResponse> =
        entraGroupDirectoryService.allMembers().map { m ->
            EntraGroupMemberResponse(
                id = m.id,
                displayName = m.displayName,
                userPrincipalName = m.userPrincipalName,
                mail = m.mail,
                odataType = m.odataType
            )
        }

    @PostMapping("/groups/refresh")
    @PreAuthorize("@authz.canMaintain()")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
        summary = "Refresh Entra Platform-System-* group membership from Microsoft Graph",
        description = "Forces a one-shot Graph reload without changing connector running state. " +
            "Requires System.Maintainer and app.entra-directory.enabled=true. " +
            "To arm/disarm the periodic schedule: POST /api/v1/connectors/entra-directory/start|stop."
    )
    fun refresh(): EntraDirectorySnapshotResponse {
        try {
            entraGroupDirectoryService.refresh(triggeredBy = auditPrincipalResolver.current())
        } catch (ex: Exception) {
            throw BadRequestException(
                "Failed to refresh Entra directory: ${ex.message ?: ex.javaClass.simpleName}"
            )
        }
        return entraGroupDirectoryService.snapshotResponse()
    }
}
