package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.jrtech.platformmanagement.connectors.consumption.blob.BlobObjectRef
import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobStorageClient
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ConsumptionBlobFilePipelineTest {

    @Test
    fun `pipeline is not a spring type and writes parquet for matching records`() {
        val storage = InMemoryBlobStorage()
        val avro = sampleAvro(
            objectType = "consumption_metric",
            usage = """{"inputToken":8,"outputToken":2}"""
        )
        storage.inputs["2024/07/01/14_30_00.avro"] = avro

        val result = ConsumptionBlobFilePipeline.create(
            inputBlobName = "2024/07/01/14_30_00.avro",
            storage = storage,
            objectType = "consumption_metric",
            dryRun = false,
            cancelRequested = { false },
            inputPrefixes = emptyList(),
            outputPrefix = "curated"
        ).run()

        assertThat(result.error).isNull()
        assertThat(result.recordsMatched).isEqualTo(1)
        assertThat(result.recordsWritten).isEqualTo(1)
        assertThat(result.outputBlob).isEqualTo("curated/2024/07/01/14_30_00.parquet")
        assertThat(storage.outputs).containsKey("curated/2024/07/01/14_30_00.parquet")
        val parquet = storage.outputs.getValue("curated/2024/07/01/14_30_00.parquet")
        assertThat(parquet.copyOfRange(0, 4)).isEqualTo("PAR1".toByteArray())
    }

    @Test
    fun `dryRun does not write output`() {
        val storage = InMemoryBlobStorage()
        storage.inputs["a.avro"] = sampleAvro("consumption_metric", """{"inputToken":1}""")

        val result = ConsumptionBlobFilePipeline.create(
            inputBlobName = "a.avro",
            storage = storage,
            objectType = "consumption_metric",
            dryRun = true,
            cancelRequested = { false }
        ).run()

        assertThat(result.recordsMatched).isEqualTo(1)
        assertThat(result.recordsWritten).isEqualTo(0)
        assertThat(storage.outputs).isEmpty()
    }

    @Test
    fun `cancel before work skips processing`() {
        val storage = InMemoryBlobStorage()
        storage.inputs["a.avro"] = sampleAvro("consumption_metric", "{}")

        val result = ConsumptionBlobFilePipeline.create(
            inputBlobName = "a.avro",
            storage = storage,
            objectType = "consumption_metric",
            dryRun = false,
            cancelRequested = { true }
        ).run()

        assertThat(result.cancelled).isTrue()
        assertThat(storage.outputs).isEmpty()
    }

    @Test
    fun `parquetNameFor swaps extension`() {
        assertThat(ConsumptionBlobFilePipeline.parquetNameFor("x/y.avro")).isEqualTo("x/y.parquet")
        assertThat(ConsumptionBlobFilePipeline.parquetNameFor("x/y")).isEqualTo("x/y.parquet")
    }

    private fun sampleAvro(objectType: String, usage: String): ByteArray {
        val schema = Schema.Parser().parse(
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
        val record = GenericData.Record(schema).apply {
            put("callerId", "c")
            put("serviceUrl", "https://llm")
            put("timestamp", "2024-07-01T14:30:00Z")
            put("objectType", objectType)
            put("usage", usage)
        }
        return ObjectTypeAvroRecordReaderTest.writeAvro(schema, listOf(record))
    }

    class InMemoryBlobStorage : ConsumptionBlobStorageClient {
        val inputs = linkedMapOf<String, ByteArray>()
        val outputs = linkedMapOf<String, ByteArray>()

        override fun listAvroBlobs(dayPathPrefix: String): List<BlobObjectRef> =
            inputs.filter { it.key.startsWith(dayPathPrefix) }
                .map { BlobObjectRef(it.key, it.value.size.toLong()) }

        override fun openBlob(blobName: String): InputStream =
            ByteArrayInputStream(inputs[blobName] ?: error("missing $blobName"))

        override fun writeOutput(
            blobName: String,
            content: ByteArray,
            metadata: Map<String, String>
        ) {
            outputs[blobName] = content
        }

        override fun listOutputBlobs(pathPrefix: String): List<BlobObjectRef> {
            val prefix = if (pathPrefix.endsWith("/")) pathPrefix else "$pathPrefix/"
            return outputs.filter { it.key.startsWith(prefix) }
                .map { BlobObjectRef(it.key, it.value.size.toLong(), etag = "etag-${it.value.size}") }
        }

        override fun readOutput(blobName: String): ByteArray =
            outputs[blobName] ?: error("missing output $blobName")
    }
}
