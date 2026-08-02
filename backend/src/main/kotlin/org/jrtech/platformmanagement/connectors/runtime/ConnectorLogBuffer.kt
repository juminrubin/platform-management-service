package org.jrtech.platformmanagement.connectors.runtime

import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.ConnectorLogSnapshotResponse
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

/**
 * In-memory ring of connector runtime log lines, capped by total UTF-8 size.
 *
 * Lines are stored in **descending** order: index 0 is the newest entry.
 * When the buffer would exceed [maxBytes], oldest lines (at the end) are dropped
 * until the newest content fits.
 *
 * Used for operator-facing [ConnectorLogSnapshotResponse] on connector info APIs
 * (not a durable audit log).
 */
class ConnectorLogBuffer(
    val maxBytes: Int = DEFAULT_MAX_BYTES
) {
    private val lock = Any()
    /** Newest first (index 0). */
    private val lines = ArrayDeque<String>()
    private var totalBytes = 0

    fun info(message: String) = append("INFO", message)

    fun warn(message: String) = append("WARN", message)

    fun error(message: String) = append("ERROR", message)

    fun append(level: String, message: String) {
        val ts = UtcTimestamps.now().toString()
        val line = "$ts $level ${message.trim()}"
        val lineBytes = utf8Size(line) + NEWLINE_BYTES
        if (lineBytes > maxBytes) {
            // Single line larger than cap: keep a truncated newest-only snapshot.
            val truncated = truncateToMaxBytes(line, maxBytes - NEWLINE_BYTES)
            synchronized(lock) {
                lines.clear()
                totalBytes = 0
                addNewestUnlocked(truncated)
            }
            return
        }
        synchronized(lock) {
            addNewestUnlocked(line)
            while (totalBytes > maxBytes && lines.isNotEmpty()) {
                // Drop oldest (tail)
                val removed = lines.removeLast()
                totalBytes -= utf8Size(removed) + NEWLINE_BYTES
            }
            if (totalBytes < 0) totalBytes = 0
        }
    }

    fun snapshot(): ConnectorLogSnapshotResponse {
        synchronized(lock) {
            val copy = lines.toList()
            return ConnectorLogSnapshotResponse(
                maxBytes = maxBytes,
                bytes = totalBytes.coerceAtLeast(0),
                lineCount = copy.size,
                lines = copy
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
            totalBytes = 0
        }
    }

    private fun addNewestUnlocked(line: String) {
        lines.addFirst(line)
        totalBytes += utf8Size(line) + NEWLINE_BYTES
    }

    companion object {
        /** Hard cap for connector log snapshots exposed via the control-plane API. */
        const val DEFAULT_MAX_BYTES: Int = 32 * 1024

        private const val NEWLINE_BYTES = 1

        private fun utf8Size(s: String): Int =
            s.toByteArray(StandardCharsets.UTF_8).size

        private fun truncateToMaxBytes(s: String, maxPayloadBytes: Int): String {
            if (maxPayloadBytes <= 0) return ""
            val bytes = s.toByteArray(StandardCharsets.UTF_8)
            if (bytes.size <= maxPayloadBytes) return s
            val ellipsis = "…"
            val ellipsisBytes = utf8Size(ellipsis)
            val contentBudget = (maxPayloadBytes - ellipsisBytes).coerceAtLeast(0)
            if (contentBudget == 0) {
                // Cap smaller than ellipsis: keep as many bytes as possible without marker.
                var end = maxPayloadBytes
                while (end > 0 && (bytes[end - 1].toInt() and 0xC0) == 0x80) {
                    end--
                }
                return if (end <= 0) "" else String(bytes, 0, end, StandardCharsets.UTF_8)
            }
            // Walk back to a valid UTF-8 boundary
            var end = contentBudget
            while (end > 0 && (bytes[end - 1].toInt() and 0xC0) == 0x80) {
                end--
            }
            if (end <= 0) return ellipsis.take(maxPayloadBytes)
            return String(bytes, 0, end, StandardCharsets.UTF_8) + ellipsis
        }
    }
}
