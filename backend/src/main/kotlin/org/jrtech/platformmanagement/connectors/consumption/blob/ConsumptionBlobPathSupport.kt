package org.jrtech.platformmanagement.connectors.consumption.blob

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builds hierarchical blob prefixes for consumption Avro storage:
 * `{optionalPrefix}/yyyy/MM/dd/`
 */
object ConsumptionBlobPathSupport {

    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

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

    /**
     * All day-directory listing prefixes for [days] × [rootPrefixes] (order: day outer, prefix inner).
     */
    fun dayDirectoryPrefixes(rootPrefixes: List<String>, days: List<LocalDate>): List<String> {
        val roots = if (rootPrefixes.isEmpty()) listOf("") else rootPrefixes
        return days.flatMap { day -> roots.map { dayDirectoryPrefix(it, day) } }
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
