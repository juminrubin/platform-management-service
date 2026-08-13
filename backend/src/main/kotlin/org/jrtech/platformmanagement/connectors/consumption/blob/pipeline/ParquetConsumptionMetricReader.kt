package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.apache.avro.generic.GenericRecord
import org.apache.parquet.avro.AvroParquetReader

/** Reads [ConsumptionMetricRow] rows from a Parquet object-container. */
class ParquetConsumptionMetricReader {
    fun read(bytes: ByteArray): List<ConsumptionMetricRow> {
        if (bytes.isEmpty()) return emptyList()
        val out = mutableListOf<ConsumptionMetricRow>()
        AvroParquetReader.builder<GenericRecord>(ByteArrayInputFile(bytes)).build().use { reader ->
            while (true) {
                val record = reader.read() ?: break
                out += fromRecord(record)
            }
        }
        return out
    }

    private fun fromRecord(record: GenericRecord): ConsumptionMetricRow =
        ConsumptionMetricRow(
            callerId = stringVal(record, "caller_id"),
            serviceUrl = stringVal(record, "service_url"),
            timestampMillis = longVal(record, "timestamp") ?: 0L,
            objectType = stringVal(record, "object_type"),
            usageJson = stringVal(record, "usage_json").ifBlank { "{}" },
            inputToken = longVal(record, "input_token"),
            outputToken = longVal(record, "output_token"),
            audioLengthSeconds = doubleVal(record, "audio_length_seconds"),
            sourceBlob = stringVal(record, "source_blob")
        )

    private fun stringVal(record: GenericRecord, name: String): String {
        val field = record.schema.getField(name) ?: return ""
        return record.get(field.pos())?.toString().orEmpty()
    }

    private fun longVal(record: GenericRecord, name: String): Long? {
        val field = record.schema.getField(name) ?: return null
        val value = record.get(field.pos()) ?: return null
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull()
        }
    }

    private fun doubleVal(record: GenericRecord, name: String): Double? {
        val field = record.schema.getField(name) ?: return null
        val value = record.get(field.pos()) ?: return null
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            else -> value.toString().toDoubleOrNull()
        }
    }
}
