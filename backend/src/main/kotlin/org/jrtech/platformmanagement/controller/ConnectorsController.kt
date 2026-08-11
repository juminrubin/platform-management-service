package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobContainerConnector
import org.jrtech.platformmanagement.connectors.consumption.eventhub.ConsumptionEventHubConnector
import org.jrtech.platformmanagement.connectors.datasource.DatasourceLoadingConnector
import org.jrtech.platformmanagement.connectors.entra.EntraDirectoryConnector
import org.jrtech.platformmanagement.connectors.runtime.ManagedConnector
import org.jrtech.platformmanagement.dto.ConnectorConfigResponse
import org.jrtech.platformmanagement.dto.ConnectorConfigureRequest
import org.jrtech.platformmanagement.dto.ConnectorInfoResponse
import org.jrtech.platformmanagement.dto.ConnectorListResponse
import org.jrtech.platformmanagement.dto.ConnectorSummaryResponse
import org.jrtech.platformmanagement.service.AuditPrincipalResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Unified Maintainer control plane for backend connector processes.
 *
 * GET  /api/v1/connectors              - list process summaries
 * GET  /api/v1/connectors/{id}         - runtime info, public config, log snapshot (max 32KB)
 * GET  /api/v1/connectors/{id}/config  - public configuration only
 * PUT  /api/v1/connectors/{id}/config  - update runtime configuration
 * POST /api/v1/connectors/{id}/start   - start / arm the process
 * POST /api/v1/connectors/{id}/stop    - stop / disarm (in-flight work completes)
 *
 * Domain data is not returned here:
 * - Entra groups/members: /api/v1/entra/groups and /api/v1/entra/members
 * - Blob import result: /api/v1/consumption/blob
 * - Consumption rows: /api/v1/consumptions
 *
 * Known path ids: consumption-storage, consumption-eventhub, entra-directory, datasource-loading.
 */
@RestController
@RequestMapping("/api/v1/connectors")
@Tag(name = "Connectors")
@SecurityRequirement(name = "bearer-jwt")
class ConnectorsController(
    private val managedConnectors: List<ManagedConnector>,
    private val eventHubConnector: ConsumptionEventHubConnector,
    private val blobConnector: ConsumptionBlobContainerConnector,
    private val entraDirectoryConnector: EntraDirectoryConnector,
    private val datasourceLoadingConnector: DatasourceLoadingConnector,
    private val auditPrincipalResolver: AuditPrincipalResolver
) {

    @GetMapping
    @PreAuthorize("@authz.canMaintain()")
    @Operation(summary = "List connector process summaries (System.Maintainer)")
    fun listConnectors(): ConnectorListResponse =
        ConnectorListResponse(
            connectors = managedConnectors.map { c ->
                val info = c.info()
                ConnectorSummaryResponse(
                    id = info.id,
                    enabled = info.enabled,
                    configured = info.configured,
                    running = info.running,
                    status = info.status,
                    detail = info.detail,
                    attributes = info.attributes
                )
            }
        )

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "Get connector runtime info",
        description = "Process status, public configuration, operational attributes, " +
            "and a log snapshot capped at 32 KB UTF-8. Does not return domain data."
    )
    fun getConnector(
        @Parameter(description = "Connector path id, e.g. consumption-storage, consumption-eventhub, entra-directory")
        @PathVariable id: String
    ): ConnectorInfoResponse = resolve(id).info()

    @GetMapping("/{id}/config")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(summary = "Get connector public configuration")
    fun getConfig(@PathVariable id: String): ConnectorConfigResponse {
        val connector = resolve(id)
        return ConnectorConfigResponse(id = connector.id.pathId, configuration = connector.configuration())
    }

    @PutMapping("/{id}/config")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "Update connector runtime configuration",
        description = "Keys are connector-specific. Example for consumption-storage: " +
            "startDate, endDate, dryRun, blobPrefixes. Example for entra-directory: " +
            "refreshIntervalMs. Example for consumption-eventhub: requireSourceRefId."
    )
    fun putConfig(
        @PathVariable id: String,
        @RequestBody body: ConnectorConfigureRequest
    ): ConnectorConfigResponse {
        val connector = resolve(id)
        val updated = connector.configure(body.configuration)
        return ConnectorConfigResponse(id = connector.id.pathId, configuration = updated)
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "Start connector process",
        description = "entra-directory: arm schedule + Graph load. " +
            "consumption-eventhub: start processor. " +
            "consumption-storage: run one import using configured startDate/endDate. " +
            "datasource-loading: entitlement check cache refresh + hourly schedule."
    )
    fun startConnector(@PathVariable id: String): ConnectorInfoResponse {
        val actor = auditPrincipalResolver.current()
        return resolve(id).start(actor)
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("@authz.canMaintain()")
    @Operation(
        summary = "Stop connector process",
        description = "Disarms the process. In-flight work is not hard-cancelled " +
            "(Entra Graph load finishes; blob import cancels between blobs)."
    )
    fun stopConnector(@PathVariable id: String): ConnectorInfoResponse {
        val actor = auditPrincipalResolver.current()
        return resolve(id).stop(actor)
    }

    private fun resolve(rawId: String): ManagedConnector {
        val connectorId = ConnectorId.requirePathId(rawId)
        return when (connectorId) {
            ConnectorId.CONSUMPTION_EVENT_HUB -> eventHubConnector
            ConnectorId.CONSUMPTION_BLOB_AVRO -> blobConnector
            ConnectorId.ENTRA_DIRECTORY -> entraDirectoryConnector
            ConnectorId.DATASOURCE_LOADING -> datasourceLoadingConnector
        }
    }
}
