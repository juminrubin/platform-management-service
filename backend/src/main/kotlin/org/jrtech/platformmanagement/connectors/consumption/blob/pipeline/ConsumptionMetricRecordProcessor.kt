package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

/**
 * Flattens a [ConsumptionMetricRecord] into a Parquet/CSV-shaped lake row.
 *
 * Known usage fields:
 * - LLM: `inputToken`, `outputToken`
 * - Text embedding: `inputToken`
 * - STT: `audioLength` (seconds)
 *
 * Unknown usage keys stay in [ConsumptionMetricRow.usageJson].
 */
class ConsumptionMetricRecordProcessor(
    private val sourceBlob: String = ""
) : RecordProcessor<ConsumptionMetricRecord, ConsumptionMetricRow>() {

    override fun process(record: ConsumptionMetricRecord): ConsumptionMetricRow? {
        val usage = parseUsage(record.usageJson)
        return ConsumptionMetricRow(
            callerId = record.callerId,
            serviceUrl = record.serviceUrl,
            timestampMillis = record.timestamp.toEpochMilli(),
            objectType = record.objectType,
            usageJson = record.usageJson,
            inputToken = longField(usage, "inputToken", "input_token"),
            outputToken = longField(usage, "outputToken", "output_token"),
            audioLengthSeconds = doubleField(usage, "audioLength", "audio_length"),
            sourceBlob = sourceBlob
        )
    }

    private fun parseUsage(raw: String): com.fasterxml.jackson.databind.JsonNode? =
        try {
            val node = PipelineJson.mapper.readTree(raw.ifBlank { "{}" })
            if (node.isObject) node else null
        } catch (_: Exception) {
            null
        }

    private fun longField(node: com.fasterxml.jackson.databind.JsonNode?, vararg names: String): Long? {
        if (node == null) return null
        for (name in names) {
            val n = node.get(name) ?: continue
            if (n.isNull || n.isMissingNode) continue
            if (n.isNumber) return n.asLong()
            n.asText()?.trim()?.toLongOrNull()?.let { return it }
        }
        return null
    }

    private fun doubleField(node: com.fasterxml.jackson.databind.JsonNode?, vararg names: String): Double? {
        if (node == null) return null
        for (name in names) {
            val n = node.get(name) ?: continue
            if (n.isNull || n.isMissingNode) continue
            if (n.isNumber) return n.asDouble()
            n.asText()?.trim()?.toDoubleOrNull()?.let { return it }
        }
        return null
    }
}
