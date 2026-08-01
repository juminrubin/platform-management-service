package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.connectors.ConnectorHealthContributor
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobImportService
import org.jrtech.platformmanagement.connectors.consumption.eventhub.ConsumptionEventHubConnector
import org.jrtech.platformmanagement.connectors.entra.EntraDirectoryConnector
import org.jrtech.platformmanagement.dto.ConnectorHealthItemResponse
import org.jrtech.platformmanagement.dto.ConnectorHealthListResponse
import org.jrtech.platformmanagement.dto.ConsumptionBlobImportRequest
import org.jrtech.platformmanagement.dto.ConsumptionBlobImportResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.service.AuditPrincipalResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Unified Maintainer control plane for connectors.
 *
 * | Method | Path | Purpose |
 * |--------|------|---------|
 * | GET | `/api/v1/connectors` | List connector health |
 * | GET | `/api/v1/connectors/{id}` | Status or connector-specific action |
 * | POST | `/api/v1/connectors/{id}/start` | Start / run (Event Hub; Entra refresh) |
 * | POST | `/api/v1/connectors/{id}/stop` | Stop (Event Hub) |
 *
 * Known `{id}` values: [ConnectorId.pathId]
 * (`consumption-storage`, `consumption-eventhub`, `entra-directory`).
 */
@RestController
@RequestMapping("/api/v1/connectors")
@Tag(name = "Connectors")
@SecurityRequirement(name = "bearer-jwt")
class ConnectorsController(
    private val healthContributors: List<ConnectorHealthContributor>,
    private val eventHubConnector: ConsumptionEventHubConnector,
    private val blobImportService: ConsumptionBlobImportService,
    private val entraDirectoryConnector: EntraDirectoryConnector,
    private val auditPrincipalResolver: AuditPrincipalResolver
) {

    @GetMapping
    @PreAuthorize("@authz.canMaintain()")
    @Operation(summary = "List connector health (System.Maintainer)")
    fun listConnectors(): ConnectorHealthListResponse =
        ConnectorHealthListResponse(
            connectors = healthContributors.map { c ->
                val h = c.health()
                ConnectorHealthItemResponse(
                    id = h.id.pathId,
                    enabled = h.enabled,
                    status = h.status,
                    detail = h.detail,
                    attributes = h.attributes
                )
            }
        )

    /**
     * Per-connector GET:
     * - `consumption-eventhub` → status snapshot
     * - `entra-directory` → load/run monitor info (not group content)
     * - `consumption-storage` → retrieve/import Avro for [startDate]..[endDate]
     */
    @GetMapping("/{id}")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "Get connector status or run connector-specific retrieve",
        description = "Requires System.Maintainer. " +
            "consumption-eventhub: status. " +
            "entra-directory: Graph load run info (use /api/v1/entra/** to view groups/members). " +
            "consumption-storage: requires startDate and endDate to import Avro."
    )
    fun getConnector(
        @Parameter(description = "Connector path id, e.g. consumption-storage, consumption-eventhub, entra-directory")
        @PathVariable id: String,

        @Parameter(description = "Required for consumption-storage: inclusive start (YYYY-MM-DD)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startDate: LocalDate?,

        @Parameter(description = "Required for consumption-storage: inclusive end (YYYY-MM-DD)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate?,

        @Parameter(description = "consumption-storage: when true, parse without DB writes")
        @RequestParam(defaultValue = "false")
        dryRun: Boolean,

        @Parameter(description = "consumption-storage: optional subset of configured blob prefixes")
        @RequestParam(required = false)
        blobPrefixes: List<String>?
    ): Any {
        return when (ConnectorId.requirePathId(id)) {
            ConnectorId.CONSUMPTION_EVENT_HUB -> eventHubConnector.status()
            ConnectorId.ENTRA_DIRECTORY -> entraDirectoryConnector.status()
            ConnectorId.CONSUMPTION_BLOB_AVRO -> retrieveStorage(
                startDate = startDate,
                endDate = endDate,
                dryRun = dryRun,
                blobPrefixes = blobPrefixes
            )
        }
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "Start or run a connector",
        description = "Requires System.Maintainer. " +
            "consumption-eventhub: start processor. " +
            "entra-directory: trigger Graph group/member refresh. " +
            "consumption-storage: not supported."
    )
    fun startConnector(
        @PathVariable id: String
    ): Any {
        val actor = auditPrincipalResolver.current()
        return when (val connectorId = ConnectorId.requirePathId(id)) {
            ConnectorId.CONSUMPTION_EVENT_HUB -> eventHubConnector.start(actor = actor)
            ConnectorId.ENTRA_DIRECTORY -> entraDirectoryConnector.start(actor = actor)
            ConnectorId.CONSUMPTION_BLOB_AVRO ->
                throw BadRequestException(
                    "Connector '${connectorId.pathId}' does not support start. " +
                        "Use GET /api/v1/connectors/consumption-storage?startDate=&endDate= to import."
                )
        }
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "Stop a connector",
        description = "Requires System.Maintainer. Supported for consumption-eventhub only."
    )
    fun stopConnector(
        @PathVariable id: String
    ): Any {
        return when (val connectorId = ConnectorId.requirePathId(id)) {
            ConnectorId.CONSUMPTION_EVENT_HUB ->
                eventHubConnector.stop(actor = auditPrincipalResolver.current())
            ConnectorId.ENTRA_DIRECTORY,
            ConnectorId.CONSUMPTION_BLOB_AVRO ->
                throw BadRequestException(
                    "Connector '${connectorId.pathId}' does not support stop. " +
                        "Use stop only for consumption-eventhub."
                )
        }
    }

    private fun retrieveStorage(
        startDate: LocalDate?,
        endDate: LocalDate?,
        dryRun: Boolean,
        blobPrefixes: List<String>?
    ): ConsumptionBlobImportResponse {
        if (startDate == null || endDate == null) {
            throw BadRequestException(
                "consumption-storage requires query parameters startDate and endDate (YYYY-MM-DD)"
            )
        }
        val request = ConsumptionBlobImportRequest(
            startDate = startDate,
            endDate = endDate,
            dryRun = dryRun,
            blobPrefixes = blobPrefixes?.takeIf { it.isNotEmpty() }
        )
        return blobImportService.importRange(
            request,
            requestedBy = auditPrincipalResolver.current()
        )
    }
}
