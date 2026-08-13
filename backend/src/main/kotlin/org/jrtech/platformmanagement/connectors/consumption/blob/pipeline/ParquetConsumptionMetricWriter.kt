package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.parquet.avro.AvroParquetWriter
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.io.OutputFile
import org.apache.parquet.io.PositionOutputStream
import java.io.ByteArrayOutputStream

/**
 * Writes [ConsumptionMetricRow] as Snappy-compressed Parquet.
 *
 * Parquet is the recommended Azure Data Lake landing format: columnar, compressed,
 * predicate-pushdown friendly, and native in Synapse / Fabric / Databricks / Spark.
 * Delta Lake is Parquet plus a transaction log — add it later only if you need
 * MERGE/ACID over these files.
 */
class ParquetConsumptionMetricWriter : OutputRecordWriter<ConsumptionMetricRow> {

    override fun write(rows: List<ConsumptionMetricRow>): ByteArray {
        val output = ByteArrayOutputFile()
        AvroParquetWriter.builder<GenericRecord>(output)
            .withSchema(SCHEMA)
            .withCompressionCodec(CompressionCodecName.SNAPPY)
            .withPageSize(DEFAULT_PAGE_SIZE)
            .build()
            .use { writer ->
                for (row in rows) {
                    writer.write(toRecord(row))
                }
            }
        return output.toByteArray()
    }

    private fun toRecord(row: ConsumptionMetricRow): GenericRecord {
        val record = GenericData.Record(SCHEMA)
        record.put("caller_id", row.callerId)
        record.put("service_url", row.serviceUrl)
        record.put("timestamp", row.timestampMillis)
        record.put("object_type", row.objectType)
        record.put("usage_json", row.usageJson)
        record.put("input_token", row.inputToken)
        record.put("output_token", row.outputToken)
        record.put("audio_length_seconds", row.audioLengthSeconds)
        record.put("source_blob", row.sourceBlob)
        return record
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 64 * 1024

        val SCHEMA: Schema = Schema.Parser().parse(
            """
            {
              "type": "record",
              "name": "ConsumptionMetric",
              "namespace": "org.jrtech.platformmanagement.consumption",
              "fields": [
                {"name": "caller_id", "type": "string"},
                {"name": "service_url", "type": "string"},
                {"name": "timestamp", "type": {"type": "long", "logicalType": "timestamp-millis"}},
                {"name": "object_type", "type": "string"},
                {"name": "usage_json", "type": "string"},
                {"name": "input_token", "type": ["null", "long"], "default": null},
                {"name": "output_token", "type": ["null", "long"], "default": null},
                {"name": "audio_length_seconds", "type": ["null", "double"], "default": null},
                {"name": "source_blob", "type": "string"}
              ]
            }
            """.trimIndent()
        )
    }
}

/** In-memory Parquet [OutputFile] so we do not need a Hadoop Path / FileSystem. */
internal class ByteArrayOutputFile : OutputFile {
    private val buffer = ByteArrayOutputStream()

    override fun create(blockSizeHint: Long): PositionOutputStream = stream()

    override fun createOrOverwrite(blockSizeHint: Long): PositionOutputStream {
        buffer.reset()
        return stream()
    }

    override fun supportsBlockSize(): Boolean = false

    override fun defaultBlockSize(): Long = 0L

    fun toByteArray(): ByteArray = buffer.toByteArray()

    private fun stream(): PositionOutputStream =
        object : PositionOutputStream() {
            override fun getPos(): Long = buffer.size().toLong()
            override fun write(b: Int) = buffer.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)
        }
}
