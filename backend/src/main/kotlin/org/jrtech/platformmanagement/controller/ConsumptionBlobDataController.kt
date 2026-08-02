package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobContainerConnector
import org.jrtech.platformmanagement.dto.ConsumptionBlobViewResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Data-plane view of Avro blobs under the **consumption-storage** connector.
 *
 * Control plane (configure / start / stop / runtime logs): `/api/v1/connectors/consumption-storage`.
 * Domain consumption rows: `/api/v1/consumptions`.
 */
@RestController
@RequestMapping("/api/v1/consumption/blob")
@Tag(name = "Consumption blob import data")
@SecurityRequirement(name = "bearer-jwt")
class ConsumptionBlobDataController(
    private val blobConnector: ConsumptionBlobContainerConnector
) {

    @GetMapping
    @PreAuthorize("@authz.canMaintain() or @authz.canRead()")
    @Operation(
        summary = "View consumption Avro blobs for a date range",
        description = "Lists hierarchical Avro blob objects for inclusive UTC calendar days " +
            "[fromDate, untilDate]. Defaults both to today (UTC). " +
            "Does not import; use connector start after configuring dates to load into the DB. " +
            "Domain rows: /api/v1/consumptions."
    )
    fun viewBlobs(
        @Parameter(description = "Inclusive start day (YYYY-MM-DD). Defaults to today UTC.")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        fromDate: LocalDate?,

        @Parameter(description = "Inclusive end day (YYYY-MM-DD). Defaults to today UTC.")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        untilDate: LocalDate?
    ): ConsumptionBlobViewResponse {
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = fromDate ?: today
        val until = untilDate ?: today
        if (until.isBefore(from)) {
            throw BadRequestException("untilDate must be on or after fromDate")
        }
        return blobConnector.viewRange(fromDate = from, untilDate = until)
    }
}
