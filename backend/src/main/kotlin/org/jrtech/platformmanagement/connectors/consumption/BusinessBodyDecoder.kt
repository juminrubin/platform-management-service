package org.jrtech.platformmanagement.connectors.consumption

import org.jrtech.platformmanagement.dto.CreateConsumptionRequest
import org.jrtech.platformmanagement.exception.BadRequestException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Decodes a business consumption payload (JSON v1) into [CreateConsumptionRequest].
 *
 * Accepts camelCase or snake_case field names. Used by Event Hub and Blob Capture paths.
 */
@Component
class BusinessBodyDecoder(
    private val objectMapper: ObjectMapper = defaultMapper()
) {
    companion object {
        fun defaultMapper(): ObjectMapper =
            jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun decodeJson(bytes: ByteArray, requireSourceRefId: Boolean = true): CreateConsumptionRequest {
        val root = try {
            objectMapper.readTree(bytes)
        } catch (ex: Exception) {
            throw BadRequestException("Invalid consumption JSON: ${ex.message}")
        }
        return decodeJsonNode(root, requireSourceRefId)
    }

    fun decodeJson(text: String, requireSourceRefId: Boolean = true): CreateConsumptionRequest =
        decodeJson(text.toByteArray(Charsets.UTF_8), requireSourceRefId)

    fun decodeJsonNode(root: JsonNode, requireSourceRefId: Boolean = true): CreateConsumptionRequest {
        val callerId = textField(root, "callerId", "caller_id")
            ?: throw BadRequestException("callerId is required")
        val serviceOfferingId = textField(root, "serviceOfferingId", "service_offering_id")
            ?: throw BadRequestException("serviceOfferingId is required")
        val sourceRefId = textField(root, "sourceRefId", "source_ref_id")
        if (requireSourceRefId && sourceRefId.isNullOrBlank()) {
            throw BadRequestException("sourceRefId is required for connector ingest")
        }

        val consumptionData = when {
            root.has("consumptionData") -> normalizeConsumptionData(root.get("consumptionData"))
            root.has("consumption_data") -> normalizeConsumptionData(root.get("consumption_data"))
            else -> "{}"
        }

        val capturedAt = instantField(root, "capturedAt", "captured_at", "consumedAt", "consumed_at")

        return CreateConsumptionRequest(
            callerId = callerId,
            serviceOfferingId = serviceOfferingId,
            sourceRefId = sourceRefId,
            consumptionData = consumptionData,
            capturedAt = capturedAt
        )
    }

    /** Optional event UUID from payload (`id` / `eventId`). */
    fun parseExternalId(root: JsonNode): java.util.UUID? {
        val raw = textField(root, "id", "eventId", "event_id") ?: return null
        return try {
            java.util.UUID.fromString(raw.trim())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun parseExternalIdFromJson(bytes: ByteArray): java.util.UUID? =
        try {
            parseExternalId(objectMapper.readTree(bytes))
        } catch (_: Exception) {
            null
        }

    private fun normalizeConsumptionData(node: JsonNode): String =
        when {
            node.isNull -> "{}"
            node.isTextual -> node.asText().ifBlank { "{}" }
            else -> objectMapper.writeValueAsString(node)
        }

    private fun textField(root: JsonNode, vararg names: String): String? {
        for (name in names) {
            val n = root.get(name) ?: continue
            if (n.isNull) continue
            val v = n.asText()?.trim().orEmpty()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun instantField(root: JsonNode, vararg names: String): Instant? {
        for (name in names) {
            val n = root.get(name) ?: continue
            if (n.isNull) continue
            if (n.isNumber) {
                // epoch millis
                return Instant.ofEpochMilli(n.asLong())
            }
            val text = n.asText()?.trim().orEmpty()
            if (text.isEmpty()) continue
            return try {
                Instant.parse(text)
            } catch (_: Exception) {
                throw BadRequestException("Invalid timestamp for $name: $text")
            }
        }
        return null
    }
}
