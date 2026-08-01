package org.jrtech.platformmanagement.connectors.consumption

import org.jrtech.platformmanagement.exception.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.time.Instant

class BusinessBodyDecoderTest {

    private val decoder = BusinessBodyDecoder(jacksonObjectMapper())

    @Test
    fun `decodes camelCase JSON`() {
        val json = """
            {
              "callerId": "a@x.com",
              "serviceOfferingId": "gpt-5.1",
              "sourceRefId": "req-1",
              "consumptionData": {"input_token": 1},
              "capturedAt": "2024-07-01T12:00:00Z"
            }
        """.trimIndent()
        val req = decoder.decodeJson(json)
        assertThat(req.callerId).isEqualTo("a@x.com")
        assertThat(req.serviceOfferingId).isEqualTo("gpt-5.1")
        assertThat(req.sourceRefId).isEqualTo("req-1")
        assertThat(req.consumptionData).contains("input_token")
        assertThat(req.capturedAt).isEqualTo(Instant.parse("2024-07-01T12:00:00Z"))
    }

    @Test
    fun `decodes snake_case and epoch millis`() {
        val json = """
            {
              "caller_id": "a@x.com",
              "service_offering_id": "gpt",
              "source_ref_id": "req-2",
              "consumption_data": "{}",
              "captured_at": 1719835200000
            }
        """.trimIndent()
        val req = decoder.decodeJson(json)
        assertThat(req.sourceRefId).isEqualTo("req-2")
        assertThat(req.capturedAt).isEqualTo(Instant.ofEpochMilli(1719835200000))
    }

    @Test
    fun `requires sourceRefId when configured`() {
        val json = """{"callerId":"a","serviceOfferingId":"b"}"""
        assertThatThrownBy { decoder.decodeJson(json, requireSourceRefId = true) }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("sourceRefId")
    }

    @Test
    fun `parses external id`() {
        val json = """{"id":"11111111-1111-1111-1111-111111111111","callerId":"a","serviceOfferingId":"b","sourceRefId":"r"}"""
        val id = decoder.parseExternalIdFromJson(json.toByteArray())
        assertThat(id.toString()).isEqualTo("11111111-1111-1111-1111-111111111111")
    }
}
