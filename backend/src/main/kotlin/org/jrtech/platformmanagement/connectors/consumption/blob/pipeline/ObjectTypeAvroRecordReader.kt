package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.apache.avro.generic.GenericRecord
import org.apache.avro.util.Utf8
import java.time.Instant

/**
 * Reads Avro consumption-metric records (`callerId`, `serviceUrl`, `timestamp`,
 * `objectType`, `usage`). Accepts camelCase or snake_case, and Event Hub Capture
 * files whose `Body` is JSON with the same fields.
 */
class ObjectTypeAvroRecordReader(
    objectTypeFilter: String = DEFAULT_OBJECT_TYPE
) : AvroRecordReader<ConsumptionMetricRecord>(objectTypeFilter) {

    override fun parseRecord(record: GenericRecord): ConsumptionMetricRecord? {
        val fromBody = extractBodyBytes(record)?.let { parseJsonPayload(it) }
        if (fromBody != null) return fromBody

        val callerId = stringField(record, "callerId", "caller_id") ?: return null
        val serviceUrl = stringField(record, "serviceUrl", "service_url") ?: return null
        val objectType = stringField(record, "objectType", "object_type") ?: objectTypeFilter
        val timestamp = instantField(record, "timestamp", "capturedAt", "captured_at")
            ?: Instant.EPOCH
        val usageJson = usageToJson(namedField(record, "usage", "Usage"))
        return ConsumptionMetricRecord(
            callerId = callerId,
            serviceUrl = serviceUrl,
            timestamp = timestamp,
            objectType = objectType,
            usageJson = usageJson
        )
    }

    private fun parseJsonPayload(bytes: ByteArray): ConsumptionMetricRecord? {
        val root = try {
            PipelineJson.mapper.readTree(bytes)
        } catch (_: Exception) {
            return null
        }
        val callerId = textNode(root, "callerId", "caller_id") ?: return null
        val serviceUrl = textNode(root, "serviceUrl", "service_url") ?: return null
        val objectType = textNode(root, "objectType", "object_type") ?: objectTypeFilter
        val timestamp = instantNode(root, "timestamp", "capturedAt", "captured_at") ?: Instant.EPOCH
        val usageNode = root.get("usage") ?: root.get("Usage")
        val usageJson = when {
            usageNode == null || usageNode.isNull -> "{}"
            usageNode.isTextual -> usageNode.asText().ifBlank { "{}" }
            else -> PipelineJson.mapper.writeValueAsString(usageNode)
        }
        return ConsumptionMetricRecord(
            callerId = callerId,
            serviceUrl = serviceUrl,
            timestamp = timestamp,
            objectType = objectType,
            usageJson = usageJson
        )
    }

    private fun namedField(record: GenericRecord, vararg names: String): Any? {
        for (name in names) {
            val field = record.schema.getField(name) ?: continue
            return record.get(field.pos())
        }
        return null
    }

    private fun usageToJson(value: Any?): String {
        if (value == null) return "{}"
        return when (value) {
            is Utf8 -> value.toString().ifBlank { "{}" }
            is CharSequence -> value.toString().ifBlank { "{}" }
            is GenericRecord -> genericRecordToJson(value)
            is Map<*, *> -> {
                val map = value.entries.associate { (k, v) -> k.toString() to avroToPlain(v) }
                PipelineJson.mapper.writeValueAsString(map)
            }
            else -> {
                val text = avroToString(value).trim()
                if (text.startsWith("{") || text.startsWith("[")) text else "{}"
            }
        }
    }

    private fun genericRecordToJson(record: GenericRecord): String {
        val map = linkedMapOf<String, Any?>()
        for (field in record.schema.fields) {
            map[field.name()] = avroToPlain(record.get(field.pos()))
        }
        return PipelineJson.mapper.writeValueAsString(map)
    }

    private fun avroToPlain(value: Any?): Any? =
        when (value) {
            null -> null
            is Utf8 -> value.toString()
            is CharSequence -> value.toString()
            is GenericRecord -> {
                value.schema.fields.associate { f -> f.name() to avroToPlain(value.get(f.pos())) }
            }
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to avroToPlain(v) }
            else -> value
        }

    private fun instantField(record: GenericRecord, vararg names: String): Instant? {
        for (name in names) {
            val field = record.schema.getField(name) ?: continue
            val value = record.get(field.pos()) ?: continue
            parseInstant(value)?.let { return it }
        }
        return null
    }

    private fun parseInstant(value: Any): Instant? =
        when (value) {
            is Long -> Instant.ofEpochMilli(value)
            is Int -> Instant.ofEpochMilli(value.toLong())
            is Utf8, is CharSequence -> parseInstantText(value.toString())
            else -> parseInstantText(value.toString())
        }

    private fun parseInstantText(raw: String): Instant? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { return Instant.ofEpochMilli(it) }
        return try {
            Instant.parse(text)
        } catch (_: Exception) {
            null
        }
    }

    private fun textNode(root: com.fasterxml.jackson.databind.JsonNode, vararg names: String): String? {
        for (name in names) {
            val n = root.get(name) ?: continue
            if (n.isNull) continue
            val text = n.asText()?.trim().orEmpty()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    private fun instantNode(root: com.fasterxml.jackson.databind.JsonNode, vararg names: String): Instant? {
        for (name in names) {
            val n = root.get(name) ?: continue
            if (n.isNull) continue
            if (n.isNumber) return Instant.ofEpochMilli(n.asLong())
            parseInstantText(n.asText())?.let { return it }
        }
        return null
    }

    companion object {
        const val DEFAULT_OBJECT_TYPE = "consumption_metric"
    }
}
