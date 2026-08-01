package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.consumption.BusinessBodyDecoder
import org.jrtech.platformmanagement.exception.BadRequestException
import org.apache.avro.Schema
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

class ConsumptionAvroFileReaderTest {

    private val reader = ConsumptionAvroFileReader(BusinessBodyDecoder(jacksonObjectMapper()))

    @Test
    fun `reads flat business avro records`() {
        val schema = Schema.Parser().parse(
            """
            {
              "type": "record",
              "name": "Consumption",
              "fields": [
                {"name": "caller_id", "type": "string"},
                {"name": "service_offering_id", "type": "string"},
                {"name": "source_ref_id", "type": "string"},
                {"name": "consumption_data", "type": "string"},
                {"name": "captured_at", "type": "string"},
                {"name": "id", "type": "string"}
              ]
            }
            """.trimIndent()
        )
        val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val record = GenericData.Record(schema).apply {
            put("caller_id", "caller@example.com")
            put("service_offering_id", "gpt-5.1")
            put("source_ref_id", "src-flat-1")
            put("consumption_data", """{"tokens":3}""")
            put("captured_at", "2024-07-01T12:00:00Z")
            put("id", id.toString())
        }
        val bytes = writeAvro(schema, listOf(record))

        val results = reader.readAll(ByteArrayInputStream(bytes), requireSourceRefId = true)
        assertThat(results).hasSize(1)
        val parsed = results[0]
        assertThat(parsed.request.callerId).isEqualTo("caller@example.com")
        assertThat(parsed.request.serviceOfferingId).isEqualTo("gpt-5.1")
        assertThat(parsed.request.sourceRefId).isEqualTo("src-flat-1")
        assertThat(parsed.request.consumptionData).contains("tokens")
        assertThat(parsed.request.capturedAt).isEqualTo(Instant.parse("2024-07-01T12:00:00Z"))
        assertThat(parsed.externalId).isEqualTo(id)
    }

    @Test
    fun `reads Event Hub Capture style Body JSON`() {
        val schema = Schema.Parser().parse(
            """
            {
              "type": "record",
              "name": "EventData",
              "fields": [
                {"name": "Body", "type": "bytes"},
                {"name": "EnqueuedTimeUtc", "type": "string"}
              ]
            }
            """.trimIndent()
        )
        val bodyJson = """
            {
              "callerId": "a@x.com",
              "serviceOfferingId": "svc-1",
              "sourceRefId": "body-ref-1",
              "consumptionData": {"n":1},
              "capturedAt": "2024-07-02T08:00:00Z",
              "id": "22222222-2222-2222-2222-222222222222"
            }
        """.trimIndent()
        val record = GenericData.Record(schema).apply {
            put("Body", ByteBuffer.wrap(bodyJson.toByteArray(Charsets.UTF_8)))
            put("EnqueuedTimeUtc", "/Date(1719835200000)/")
        }
        val bytes = writeAvro(schema, listOf(record))

        val results = reader.readAll(ByteArrayInputStream(bytes), requireSourceRefId = true)
        assertThat(results).hasSize(1)
        assertThat(results[0].request.callerId).isEqualTo("a@x.com")
        assertThat(results[0].request.sourceRefId).isEqualTo("body-ref-1")
        assertThat(results[0].externalId.toString()).isEqualTo("22222222-2222-2222-2222-222222222222")
    }

    @Test
    fun `requires sourceRefId on flat records when configured`() {
        val schema = Schema.Parser().parse(
            """
            {
              "type": "record",
              "name": "Consumption",
              "fields": [
                {"name": "callerId", "type": "string"},
                {"name": "serviceOfferingId", "type": "string"}
              ]
            }
            """.trimIndent()
        )
        val record = GenericData.Record(schema).apply {
            put("callerId", "a")
            put("serviceOfferingId", "b")
        }
        val bytes = writeAvro(schema, listOf(record))

        assertThatThrownBy {
            reader.readAll(ByteArrayInputStream(bytes), requireSourceRefId = true)
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("sourceRefId")
    }

    private fun writeAvro(schema: Schema, records: List<GenericRecord>): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = DataFileWriter(GenericDatumWriter<GenericRecord>(schema))
        writer.create(schema, out)
        records.forEach { writer.append(it) }
        writer.close()
        return out.toByteArray()
    }
}
