package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.apache.avro.Schema
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Instant

class ObjectTypeAvroRecordReaderTest {

    private val reader = ObjectTypeAvroRecordReader("consumption_metric")

    @Test
    fun `keeps matching object_type and drops others`() {
        val schema = flatSchema()
        val keep = GenericData.Record(schema).apply {
            put("callerId", "c1")
            put("serviceUrl", "https://llm.example/chat")
            put("timestamp", "2024-07-01T12:00:00Z")
            put("objectType", "consumption_metric")
            put("usage", """{"inputToken":10,"outputToken":4}""")
        }
        val skip = GenericData.Record(schema).apply {
            put("callerId", "c2")
            put("serviceUrl", "https://other")
            put("timestamp", "2024-07-01T12:00:01Z")
            put("objectType", "heartbeat")
            put("usage", "{}")
        }
        val results = reader.readMatching(ByteArrayInputStream(writeAvro(schema, listOf(keep, skip))))
        assertThat(results).hasSize(1)
        assertThat(results[0].callerId).isEqualTo("c1")
        assertThat(results[0].serviceUrl).isEqualTo("https://llm.example/chat")
        assertThat(results[0].timestamp).isEqualTo(Instant.parse("2024-07-01T12:00:00Z"))
        assertThat(results[0].objectType).isEqualTo("consumption_metric")
        assertThat(results[0].usageJson).contains("inputToken")
    }

    @Test
    fun `reads Event Hub Capture Body JSON`() {
        val schema = Schema.Parser().parse(
            """
            {
              "type": "record",
              "name": "EventData",
              "fields": [
                {"name": "Body", "type": "bytes"}
              ]
            }
            """.trimIndent()
        )
        val body = """
            {
              "callerId": "a@x.com",
              "serviceUrl": "https://stt.example/transcribe",
              "timestamp": "2024-07-02T08:00:00Z",
              "objectType": "consumption_metric",
              "usage": {"audioLength": 12.5}
            }
        """.trimIndent()
        val record = GenericData.Record(schema).apply {
            put("Body", ByteBuffer.wrap(body.toByteArray(Charsets.UTF_8)))
        }
        val results = reader.readMatching(ByteArrayInputStream(writeAvro(schema, listOf(record))))
        assertThat(results).hasSize(1)
        assertThat(results[0].callerId).isEqualTo("a@x.com")
        assertThat(results[0].usageJson).contains("audioLength")
    }

    @Test
    fun `accepts snake_case field names`() {
        val schema = Schema.Parser().parse(
            """
            {
              "type": "record",
              "name": "Consumption",
              "fields": [
                {"name": "caller_id", "type": "string"},
                {"name": "service_url", "type": "string"},
                {"name": "timestamp", "type": "long"},
                {"name": "object_type", "type": "string"},
                {"name": "usage", "type": "string"}
              ]
            }
            """.trimIndent()
        )
        val instant = Instant.parse("2024-07-01T00:00:00Z")
        val record = GenericData.Record(schema).apply {
            put("caller_id", "embed")
            put("service_url", "https://embed.example")
            put("timestamp", instant.toEpochMilli())
            put("object_type", "consumption_metric")
            put("usage", """{"inputToken":32}""")
        }
        val results = reader.readMatching(ByteArrayInputStream(writeAvro(schema, listOf(record))))
        assertThat(results.single().callerId).isEqualTo("embed")
        assertThat(results.single().timestamp).isEqualTo(instant)
    }

    private fun flatSchema(): Schema = Schema.Parser().parse(
        """
        {
          "type": "record",
          "name": "Consumption",
          "fields": [
            {"name": "callerId", "type": "string"},
            {"name": "serviceUrl", "type": "string"},
            {"name": "timestamp", "type": "string"},
            {"name": "objectType", "type": "string"},
            {"name": "usage", "type": "string"}
          ]
        }
        """.trimIndent()
    )

    companion object {
        fun writeAvro(schema: Schema, records: List<GenericRecord>): ByteArray {
            val out = ByteArrayOutputStream()
            val writer = DataFileWriter(GenericDatumWriter<GenericRecord>(schema))
            writer.create(schema, out)
            records.forEach { writer.append(it) }
            writer.close()
            return out.toByteArray()
        }
    }
}
