package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.ConnectorHealthContributor
import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConsumptionBlobConnectorStatusResponse
import org.jrtech.platformmanagement.dto.ConsumptionBlobImportRequest
import org.jrtech.platformmanagement.dto.ConsumptionBlobImportResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.service.ConsumptionService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * On-demand import of hierarchical consumption Avro files from Azure Blob.
 *
 * Layout under each configured root prefix: `{blobPrefix}/yyyy/MM/dd/HH_mm_ss.avro`
 */
@Service
class ConsumptionBlobImportService(
    private val connectorsProperties: ConnectorsProperties,
    private val storageClientProvider: ObjectProvider<ConsumptionBlobStorageClient>,
    private val avroFileReader: ConsumptionAvroFileReader,
    private val consumptionService: ConsumptionService
) : ConnectorHealthContributor {

    private val log = logger()

    override val id: ConnectorId = ConnectorId.CONSUMPTION_BLOB_AVRO

    override fun isEnabled(): Boolean = connectorsProperties.consumptionBlob.enabled

    fun status(): ConsumptionBlobConnectorStatusResponse {
        val p = connectorsProperties.consumptionBlob
        val prefixes = p.resolvedBlobPrefixes()
        @Suppress("DEPRECATION")
        return ConsumptionBlobConnectorStatusResponse(
            id = id.pathId,
            enabled = p.enabled,
            configured = p.isConfigured(),
            storageAccountUrl = p.storageAccountUrl.ifBlank { null },
            container = p.container.ifBlank { null },
            blobPrefixes = prefixes,
            blobPrefix = prefixes.firstOrNull { it.isNotEmpty() },
            maxRangeDays = p.maxRangeDays,
            maxBlobsPerJob = p.maxBlobsPerJob,
            requireSourceRefId = p.requireSourceRefId,
            detail = when {
                !p.enabled -> "disabled"
                !p.isConfigured() -> "incomplete-config (need container + storage-account-url or connection-string)"
                else -> "ready"
            }
        )
    }

    override fun health(): ConnectorHealthView {
        val s = status()
        val statusLabel = when {
            !s.enabled -> "DISABLED"
            !s.configured -> "DOWN"
            else -> "UP"
        }
        return ConnectorHealthView(
            id = id,
            enabled = s.enabled,
            status = statusLabel,
            detail = s.detail,
            attributes = buildMap {
                s.container?.let { put("container", it) }
                put(
                    "blobPrefixes",
                    s.blobPrefixes.joinToString(",") { p -> if (p.isEmpty()) "(root)" else p }
                )
            }
        )
    }

    /**
     * Lists day folders in [startDate]..[endDate] under each configured (or requested) root
     * prefix, downloads matching Avro files, parses records, and optionally imports them via
     * [ConsumptionService.createFromImport].
     */
    fun importRange(request: ConsumptionBlobImportRequest, requestedBy: String): ConsumptionBlobImportResponse {
        val props = connectorsProperties.consumptionBlob
        if (!props.enabled) {
            throw BadRequestException(
                "Consumption blob connector is disabled " +
                    "(set app.connectors.consumption-blob.enabled=true)"
            )
        }
        if (!props.isConfigured()) {
            throw BadRequestException(
                "Consumption blob connector is not configured " +
                    "(container + storage-account-url or connection-string required)"
            )
        }
        val storage = storageClientProvider.getIfAvailable()
            ?: throw BadRequestException("Consumption blob storage client is not available")

        val start = request.startDate
        val end = request.endDate
        if (end.isBefore(start)) {
            throw BadRequestException("endDate must be on or after startDate")
        }
        val rangeDays = ChronoUnit.DAYS.between(start, end) + 1
        if (rangeDays > props.maxRangeDays) {
            throw BadRequestException(
                "Date range spans $rangeDays days; max allowed is ${props.maxRangeDays}"
            )
        }

        val rootPrefixes = resolveImportPrefixes(request.blobPrefixes, props.resolvedBlobPrefixes())

        val startedAt = UtcTimestamps.now()
        val days = ConsumptionBlobPathSupport.daysInclusive(start, end)
        val errors = mutableListOf<String>()
        var blobsDiscovered = 0
        var blobsProcessed = 0
        var blobsFailed = 0
        var rowsParsed = 0
        var rowsInserted = 0
        var rowsDuplicate = 0
        var rowsInvalid = 0
        var rowsFailed = 0

        val listingPrefixes = ConsumptionBlobPathSupport.dayDirectoryPrefixes(rootPrefixes, days)
        // De-dupe blob names if prefixes overlap
        val seenBlobNames = LinkedHashSet<String>()
        val blobQueue = mutableListOf<BlobObjectRef>()
        for (prefix in listingPrefixes) {
            try {
                val listed = storage.listAvroBlobs(prefix)
                for (blob in listed) {
                    if (seenBlobNames.add(blob.name)) {
                        blobQueue += blob
                        blobsDiscovered++
                    }
                }
            } catch (ex: Exception) {
                val msg = "Failed listing prefix $prefix: ${ex.message}"
                log.error(msg, ex)
                errors += msg
            }
        }

        if (blobQueue.size > props.maxBlobsPerJob) {
            throw BadRequestException(
                "Discovered ${blobQueue.size} Avro blobs; max per request is ${props.maxBlobsPerJob}. " +
                    "Narrow startDate/endDate or raise app.connectors.consumption-blob.max-blobs-per-job."
            )
        }

        for (blob in blobQueue) {
            try {
                storage.openBlob(blob.name).use { input ->
                    val records = avroFileReader.readAll(input, props.requireSourceRefId)
                    rowsParsed += records.size
                    for (parsed in records) {
                        if (request.dryRun) {
                            continue
                        }
                        try {
                            val result = consumptionService.createFromImport(
                                parsed.request,
                                parsed.externalId
                            )
                            if (result.created) rowsInserted++ else rowsDuplicate++
                        } catch (ex: BadRequestException) {
                            rowsInvalid++
                            errors += "${blob.name}: ${ex.message}"
                        } catch (ex: ResourceNotFoundException) {
                            rowsFailed++
                            errors += "${blob.name}: ${ex.message}"
                        } catch (ex: Exception) {
                            rowsFailed++
                            errors += "${blob.name}: ${ex.message}"
                            log.warn("Failed importing record from {}: {}", blob.name, ex.message)
                        }
                    }
                }
                blobsProcessed++
            } catch (ex: Exception) {
                blobsFailed++
                val msg = "Failed blob ${blob.name}: ${ex.message}"
                log.error(msg, ex)
                errors += msg
            }
        }

        val finishedAt = UtcTimestamps.now()
        log.info(
            "Blob import finished by={} start={} end={} dryRun={} prefixes={} blobs={}/{} rows parsed={} inserted={} dup={} invalid={} failed={}",
            requestedBy,
            start,
            end,
            request.dryRun,
            rootPrefixes.joinToString(",") { p -> if (p.isEmpty()) "(root)" else p },
            blobsProcessed,
            blobsDiscovered,
            rowsParsed,
            rowsInserted,
            rowsDuplicate,
            rowsInvalid,
            rowsFailed
        )

        return ConsumptionBlobImportResponse(
            startDate = start,
            endDate = end,
            dryRun = request.dryRun,
            requestedBy = requestedBy,
            startedAt = startedAt,
            finishedAt = finishedAt,
            blobPrefixes = rootPrefixes,
            daysVisited = days.size,
            blobsDiscovered = blobsDiscovered,
            blobsProcessed = blobsProcessed,
            blobsFailed = blobsFailed,
            rowsParsed = rowsParsed,
            rowsInserted = if (request.dryRun) 0 else rowsInserted,
            rowsDuplicate = if (request.dryRun) 0 else rowsDuplicate,
            rowsInvalid = rowsInvalid,
            rowsFailed = rowsFailed,
            errors = errors.take(100)
        )
    }

    /**
     * When the request supplies prefixes, each must match a configured resolved prefix
     * (after normalize). Empty request list → use all configured.
     */
    private fun resolveImportPrefixes(
        requested: List<String>?,
        configured: List<String>
    ): List<String> {
        if (requested.isNullOrEmpty()) {
            return configured
        }
        val configuredSet = configured.toSet()
        val selected = LinkedHashSet<String>()
        for (raw in requested) {
            val normalized = ConsumptionBlobPathSupport.normalizeRootPrefix(raw)
            if (normalized !in configuredSet) {
                val allowed = configured.joinToString(", ") { p -> if (p.isEmpty()) "(root)" else p }
                throw BadRequestException(
                    "blobPrefixes entry '${if (raw.isBlank()) "(root)" else raw}' is not configured. " +
                        "Allowed: $allowed"
                )
            }
            selected += normalized
        }
        return selected.toList()
    }
}
