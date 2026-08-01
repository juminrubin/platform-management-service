package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.consumption.BusinessBodyDecoder
import org.jrtech.platformmanagement.dto.CreateConsumptionRequest
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.logging.logger
import org.apache.avro.file.DataFileStream
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.util.Utf8
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

/**
 * Parses consumption Avro object container files.
 *
 * Supports:
 * 1. **Event Hub Capture-style** records with a `Body` (bytes / string) holding JSON business payload
 * 2. **Flat business records** with fields such as caller_id / service_offering_id / source_ref_id
 */
@Component
class ConsumptionAvroFileReader(
    private val bodyDecoder: BusinessBodyDecoder
) {
    private val log = logger()

    data class AvroRecordResult(
        val request: CreateConsumptionRequest,
        val externalId: UUID?
    )

    /**
     * Reads all records from an Avro object container stream.
     * Does not close [input]; caller owns the stream lifecycle.
     */
    fun readAll(
        input: InputStream,
        requireSourceRefId: Boolean
    ): List<AvroRecordResult> {
        val reader = GenericDatumReader<GenericRecord>()
        DataFileStream(input, reader).use { stream ->
            val out = mutableListOf<AvroRecordResult>()
            while (stream.hasNext()) {
                val record = stream.next()
                out += parseRecord(record, requireSourceRefId)
            }
            return out
        }
    }

    fun parseRecord(record: GenericRecord, requireSourceRefId: Boolean): AvroRecordResult {
        // 1) Event Hub Capture envelope: Body holds business JSON
        val bodyBytes = extractBodyBytes(record)
        if (bodyBytes != null && bodyBytes.isNotEmpty()) {
            val request = bodyDecoder.decodeJson(bodyBytes, requireSourceRefId)
            val externalId = bodyDecoder.parseExternalIdFromJson(bodyBytes)
            return AvroRecordResult(request = request, externalId = externalId)
        }

        // 2) Flat / business Avro schema
        val callerId = stringField(record, "callerId", "caller_id")
            ?: throw BadRequestException("Avro record missing callerId/caller_id (and no Body)")
        val serviceOfferingId = stringField(record, "serviceOfferingId", "service_offering_id")
            ?: throw BadRequestException("Avro record missing serviceOfferingId/service_offering_id")
        val sourceRefId = stringField(record, "sourceRefId", "source_ref_id")
        if (requireSourceRefId && sourceRefId.isNullOrBlank()) {
            throw BadRequestException("Avro record missing sourceRefId/source_ref_id")
        }
        val consumptionData = stringField(record, "consumptionData", "consumption_data") ?: "{}"
        val capturedAt = instantField(record, "capturedAt", "captured_at", "consumedAt", "consumed_at")
            ?: enqueuedTimeAsInstant(record)
        val externalId = uuidField(record, "id", "eventId", "event_id")

        return AvroRecordResult(
            request = CreateConsumptionRequest(
                callerId = callerId,
                serviceOfferingId = serviceOfferingId,
                sourceRefId = sourceRefId,
                consumptionData = consumptionData,
                capturedAt = capturedAt
            ),
            externalId = externalId
        )
    }

    private fun extractBodyBytes(record: GenericRecord): ByteArray? {
        val schema = record.schema
        val field = schema.getField("Body") ?: schema.getField("body") ?: return null
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
            else -> {
                log.debug("Unsupported Body type: {}", value.javaClass.name)
                null
            }
        }
    }

    private fun enqueuedTimeAsInstant(record: GenericRecord): Instant? {
        val raw = stringField(record, "EnqueuedTimeUtc", "enqueuedTimeUtc") ?: return null
        // Capture often uses "/Date(1719835200000)/"
        val ms = Regex("""/Date\((\d+)\)/""").find(raw)?.groupValues?.get(1)?.toLongOrNull()
        if (ms != null) return Instant.ofEpochMilli(ms)
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun stringField(record: GenericRecord, vararg names: String): String? {
        for (name in names) {
            val field = record.schema.getField(name) ?: continue
            val value = record.get(field.pos()) ?: continue
            val text = when (value) {
                is Utf8 -> value.toString()
                is CharSequence -> value.toString()
                else -> value.toString()
            }.trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    private fun instantField(record: GenericRecord, vararg names: String): Instant? {
        for (name in names) {
            val field = record.schema.getField(name) ?: continue
            val value = record.get(field.pos()) ?: continue
            when (value) {
                is Long -> return Instant.ofEpochMilli(value)
                is Int -> return Instant.ofEpochMilli(value.toLong())
                is Utf8, is CharSequence -> {
                    val text = value.toString().trim()
                    if (text.isEmpty()) continue
                    return try {
                        Instant.parse(text)
                    } catch (_: Exception) {
                        throw BadRequestException("Invalid timestamp field $name: $text")
                    }
                }
            }
        }
        return null
    }

    private fun uuidField(record: GenericRecord, vararg names: String): UUID? {
        val raw = stringField(record, *names) ?: return null
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
