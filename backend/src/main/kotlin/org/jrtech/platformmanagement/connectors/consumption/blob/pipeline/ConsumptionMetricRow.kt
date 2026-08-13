package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

/**
 * One processed lake row written to Parquet.
 *
 * Common identity columns are typed. Service-specific usage is both flattened
 * (nullable metric columns) and preserved as [usageJson] for unknown fields.
 */
data class ConsumptionMetricRow(
    val callerId: String,
    val serviceUrl: String,
    val timestampMillis: Long,
    val objectType: String,
    val usageJson: String,
    val inputToken: Long? = null,
    val outputToken: Long? = null,
    val audioLengthSeconds: Double? = null,
    val sourceBlob: String = ""
)
