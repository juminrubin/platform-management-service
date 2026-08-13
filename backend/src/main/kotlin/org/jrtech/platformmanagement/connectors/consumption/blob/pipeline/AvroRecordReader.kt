package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.apache.avro.file.DataFileStream
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.util.Utf8
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Reads every record from an Avro object-container stream and keeps only those
 * whose `object_type` / `objectType` equals [objectTypeFilter].
 *
 * Not a Spring bean — the file pipeline constructs a reader per Avro file.
 */
abstract class AvroRecordReader<T>(
    protected val objectTypeFilter: String
) {
    /**
     * Iterate [input], drop records that do not match [objectTypeFilter],
     * and map the rest via [parseRecord]. Does not close [input].
     */
    fun readMatching(input: InputStream): List<T> {
        val expected = objectTypeFilter.trim()
        require(expected.isNotEmpty()) { "objectTypeFilter must not be blank" }
        val out = mutableListOf<T>()
        val reader = GenericDatumReader<GenericRecord>()
        DataFileStream(input, reader).use { stream ->
            while (stream.hasNext()) {
                val record = stream.next()
                val objectType = objectTypeOf(record) ?: continue
                if (!objectType.equals(expected, ignoreCase = true)) continue
                parseRecord(record)?.let { out += it }
            }
        }
        return out
    }

    /** Map a filtered Avro record to the domain type, or null to skip. */
    protected abstract fun parseRecord(record: GenericRecord): T?

    /**
     * `object_type` from a top-level Avro field or from an Event Hub Capture
     * `Body` JSON payload.
     */
    protected open fun objectTypeOf(record: GenericRecord): String? {
        stringField(record, "objectType", "object_type")?.let { return it }
        val body = extractBodyBytes(record) ?: return null
        return jsonTextField(body, "objectType", "object_type")
    }

    protected fun stringField(record: GenericRecord, vararg names: String): String? {
        for (name in names) {
            val field = record.schema.getField(name) ?: continue
            val value = record.get(field.pos()) ?: continue
            val text = avroToString(value).trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    protected fun extractBodyBytes(record: GenericRecord): ByteArray? {
        val field = record.schema.getField("Body") ?: record.schema.getField("body") ?: return null
        val value = record.get(field.pos()) ?: return null
        return when (value) {
            is ByteBuffer -> {
                val dup = value.duplicate()
                val arr = ByteArray(dup.remaining())
                dup.get(arr)
                arr
            }
            is ByteArray -> value
            is Utf8 -> value.toString().toByteArray(Charsets.UTF_8)
            is CharSequence -> value.toString().toByteArray(Charsets.UTF_8)
            else -> null
        }
    }

    protected fun avroToString(value: Any): String =
        when (value) {
            is Utf8 -> value.toString()
            is CharSequence -> value.toString()
            is ByteBuffer -> {
                val dup = value.duplicate()
                val arr = ByteArray(dup.remaining())
                dup.get(arr)
                String(arr, Charsets.UTF_8)
            }
            is ByteArray -> String(value, Charsets.UTF_8)
            else -> value.toString()
        }

    protected fun jsonTextField(json: ByteArray, vararg names: String): String? {
        val node = try {
            PipelineJson.mapper.readTree(json)
        } catch (_: Exception) {
            return null
        }
        for (name in names) {
            val n = node.get(name) ?: continue
            if (n.isNull) continue
            val text = n.asText()?.trim().orEmpty()
            if (text.isNotEmpty()) return text
        }
        return null
    }
}
