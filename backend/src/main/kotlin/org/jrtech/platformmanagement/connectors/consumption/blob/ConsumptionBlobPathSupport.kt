package org.jrtech.platformmanagement.connectors.consumption.blob

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builds hierarchical blob prefixes for consumption Avro storage:
 * `{optionalPrefix}/yyyy/MM/dd/`
 */
object ConsumptionBlobPathSupport {

    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    private val FIVE_MINUTE_PARQUET = Regex("""^\d{2}_\d{2}_\d{2}\.parquet$""", RegexOption.IGNORE_CASE)

    fun normalizeRootPrefix(blobPrefix: String): String =
        blobPrefix.trim().trim('/').let { if (it.isEmpty()) "" else it }

    /**
     * Prefix for listing all blobs under a calendar day (ends with `/`).
     */
    fun dayDirectoryPrefix(blobPrefix: String, day: LocalDate): String {
        val root = normalizeRootPrefix(blobPrefix)
        val dayPath = day.format(dayFormatter)
        return if (root.isEmpty()) {
            "$dayPath/"
        } else {
            "$root/$dayPath/"
        }
    }

    /** Day-directory listing prefixes under one [rootPrefix] (empty = container root). */
    fun dayDirectoryPrefixes(rootPrefix: String, days: List<LocalDate>): List<String> =
        dayDirectoryPrefixes(listOf(rootPrefix), days)

    /**
     * Day-directory listing prefixes for [days] × [rootPrefixes]
     * (order: day outer, prefix inner). Empty list → container root only.
     */
    fun dayDirectoryPrefixes(rootPrefixes: List<String>, days: List<LocalDate>): List<String> {
        val roots = if (rootPrefixes.isEmpty()) listOf("") else rootPrefixes
        return days.flatMap { day -> roots.map { dayDirectoryPrefix(it, day) } }
    }

    /**
     * Map an input Avro path to an output Parquet path under a single output root.
     * The longest matching input prefix is stripped.
     *
     * `eh-capture/2024/07/01/14_30_00.avro` + inputs `[eh-capture, manual/import]`
     * + output `curated` → `curated/2024/07/01/14_30_00.parquet`
     */
    fun parquetOutputName(
        inputBlobName: String,
        inputPrefix: String,
        outputPrefix: String
    ): String = parquetOutputName(inputBlobName, listOf(inputPrefix), outputPrefix)

    fun parquetOutputName(
        inputBlobName: String,
        inputPrefixes: List<String>,
        outputPrefix: String
    ): String {
        val parquet = inputBlobName.trim().let { name ->
            if (name.endsWith(".avro", ignoreCase = true)) name.dropLast(5) + ".parquet"
            else "$name.parquet"
        }
        val match = inputPrefixes
            .map { normalizeRootPrefix(it) }
            .filter { it.isNotEmpty() }
            .filter { parquet == it || parquet.startsWith("$it/") }
            .maxByOrNull { it.length }
        val relative = if (match == null) parquet else parquet.removePrefix(match).trimStart('/')
        val outRoot = normalizeRootPrefix(outputPrefix)
        return if (outRoot.isEmpty()) relative else "$outRoot/$relative"
    }

    /**
     * Daily curated object: `{outputPrefix}/yyyy/MM/dd.parquet`.
     * Prefer a prefix distinct from the 5-minute landing folder so this is
     * not a sibling of `{sourcePrefix}/yyyy/MM/dd/`.
     */
    fun dailyParquetName(outputPrefix: String, day: LocalDate): String {
        val ymd = day.format(dayFormatter)
        val root = normalizeRootPrefix(outputPrefix)
        return if (root.isEmpty()) "$ymd.parquet" else "$root/$ymd.parquet"
    }

    fun isFiveMinuteParquet(blobName: String): Boolean {
        val fileName = blobName.substringAfterLast('/').trim()
        return FIVE_MINUTE_PARQUET.matches(fileName)
    }

    fun daysInclusive(start: LocalDate, end: LocalDate): List<LocalDate> {
        require(!end.isBefore(start)) { "endDate must be on or after startDate" }
        val days = mutableListOf<LocalDate>()
        var d = start
        while (!d.isAfter(end)) {
            days += d
            d = d.plusDays(1)
        }
        return days
    }
}
