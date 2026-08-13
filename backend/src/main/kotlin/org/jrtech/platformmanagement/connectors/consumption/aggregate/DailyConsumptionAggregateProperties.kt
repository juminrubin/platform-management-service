package org.jrtech.platformmanagement.connectors.consumption.aggregate

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Daily Parquet compact job (`app.connectors.daily-consumption-aggregate`).
 *
 * Reads 5-minute Parquet files written by consumption-storage and writes one
 * file for **yesterday** (UTC). Uses the same blob account / output container
 * as `app.connectors.consumption-blob`. Daily files can live under a different
 * prefix so they do not sit next to `{source-prefix}/yyyy/MM/dd/`.
 */
@ConfigurationProperties(prefix = "app.connectors.daily-consumption-aggregate")
data class DailyConsumptionAggregateProperties(
    val enabled: Boolean = false,
    /** Arm the daily schedule on ApplicationReady. */
    val autoStart: Boolean = false,
    /** UTC hour (0–23) when yesterday is compacted. Default 01:00 UTC. */
    val runHourUtc: Int = 1,
    /**
     * Root folder in the output container for daily Parquet
     * (`{prefix}/yyyy/MM/dd.parquet`). Empty falls back to
     * `app.connectors.consumption-blob.output-blob-prefix`.
     * Env: `APP_CONNECTOR_DAILY_AGG_OUTPUT_PREFIX`.
     */
    val outputBlobPrefix: String = ""
) {
    fun resolvedRunHourUtc(): Int = runHourUtc.coerceIn(0, 23)

    /** Normalized daily-file prefix; [fallback] when this property is blank. */
    fun resolvedOutputBlobPrefix(fallback: String = ""): String {
        val own = outputBlobPrefix.trim().trim('/')
        return own.ifEmpty { fallback.trim().trim('/') }
    }
}
