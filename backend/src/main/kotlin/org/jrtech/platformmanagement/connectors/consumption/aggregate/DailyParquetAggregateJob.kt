package org.jrtech.platformmanagement.connectors.consumption.aggregate

import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobPathSupport
import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobStorageClient
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ParquetConsumptionMetricReader
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ParquetConsumptionMetricWriter
import org.jrtech.platformmanagement.logging.logger
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Compacts one UTC day's 5-minute Parquet files into a single daily file.
 * Not a Spring bean — the connector runner instantiates this per day.
 *
 * [sourcePrefix] is the 5-minute landing folder (consumption-blob output prefix).
 * [outputPrefix] is the daily-file folder and may differ.
 */
class DailyParquetAggregateJob(
    private val day: LocalDate,
    private val storage: ConsumptionBlobStorageClient,
    private val sourcePrefix: String,
    private val outputPrefix: String,
    private val reader: ParquetConsumptionMetricReader = ParquetConsumptionMetricReader(),
    private val writer: ParquetConsumptionMetricWriter = ParquetConsumptionMetricWriter()
) {
    private val log = logger()

    fun sourceDirectory(): String = ConsumptionBlobPathSupport.dayDirectoryPrefix(sourcePrefix, day)

    fun outputBlobName(): String = ConsumptionBlobPathSupport.dailyParquetName(outputPrefix, day)

    fun listSourceFiles() =
        storage.listOutputBlobs(sourceDirectory())
            .filter { ConsumptionBlobPathSupport.isFiveMinuteParquet(it.name) }
            .sortedBy { it.name }

    fun fingerprint(files: List<org.jrtech.platformmanagement.connectors.consumption.blob.BlobObjectRef>): String {
        val canon = files.joinToString("|") { "${it.name}:${it.etag}:${it.size}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canon.toByteArray())
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun run(): DailyAggregateResult {
        val sources = listSourceFiles()
        val outputName = outputBlobName()
        if (sources.isEmpty()) {
            log.info("Daily aggregate {}: no 5-minute parquet under {}", day, sourceDirectory())
            return DailyAggregateResult(
                day = day,
                outputBlob = null,
                sourceFiles = 0,
                rowsWritten = 0,
                fingerprint = fingerprint(sources)
            )
        }
        val rows = sources.flatMap { ref ->
            reader.read(storage.readOutput(ref.name))
        }
        val bytes = writer.write(rows)
        storage.writeOutput(
            outputName,
            bytes,
            mapOf(
                "aggregate_day" to day.toString(),
                "source_files" to sources.size.toString(),
                "source_fingerprint" to fingerprint(sources)
            )
        )
        log.info(
            "Daily aggregate {} wrote {} row(s) from {} file(s) -> {} ({} bytes)",
            day,
            rows.size,
            sources.size,
            outputName,
            bytes.size
        )
        return DailyAggregateResult(
            day = day,
            outputBlob = outputName,
            sourceFiles = sources.size,
            rowsWritten = rows.size,
            fingerprint = fingerprint(sources)
        )
    }
}

data class DailyAggregateResult(
    val day: LocalDate,
    val outputBlob: String?,
    val sourceFiles: Int,
    val rowsWritten: Int,
    val fingerprint: String,
    val skipped: Boolean = false,
    val skipReason: String? = null,
    val error: String? = null
)
