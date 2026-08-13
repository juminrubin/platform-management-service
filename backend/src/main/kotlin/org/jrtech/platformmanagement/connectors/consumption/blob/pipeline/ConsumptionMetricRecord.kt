package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import java.time.Instant

/**
 * One Avro input record after [object_type] filtering.
 *
 * [usageJson] is the raw usage object (service-specific). Known shapes:
 * - LLM: `inputToken`, `outputToken`
 * - Text embedding: `inputToken`
 * - STT: `audioLength` (seconds)
 */
data class ConsumptionMetricRecord(
    val callerId: String,
    val serviceUrl: String,
    val timestamp: Instant,
    val objectType: String,
    val usageJson: String
)
