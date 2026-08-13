package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobPathSupport
import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobStorageClient
import org.jrtech.platformmanagement.logging.logger

/**
 * One Avro file → filter → process → Parquet write.
 *
 * Not a Spring bean. The connector thread runner constructs a new instance per
 * input blob and submits [run] to a worker thread.
 */
class ConsumptionBlobFilePipeline(
    private val inputBlobName: String,
    private val outputBlobName: String,
    private val storage: ConsumptionBlobStorageClient,
    private val reader: AvroRecordReader<ConsumptionMetricRecord>,
    private val processor: RecordProcessor<ConsumptionMetricRecord, ConsumptionMetricRow>,
    private val writer: OutputRecordWriter<ConsumptionMetricRow>,
    private val dryRun: Boolean = false,
    private val cancelRequested: () -> Boolean = { false },
    private val outputMetadata: Map<String, String> = emptyMap()
) {
    private val log = logger()

    fun run(): FilePipelineResult {
        if (cancelRequested()) {
            return FilePipelineResult(
                inputBlob = inputBlobName,
                outputBlob = null,
                recordsRead = 0,
                recordsMatched = 0,
                recordsWritten = 0,
                recordsInvalid = 0,
                cancelled = true
            )
        }
        return try {
            val matched = storage.openBlob(inputBlobName).use { input ->
                reader.readMatching(input)
            }
            if (cancelRequested()) {
                return FilePipelineResult(
                    inputBlob = inputBlobName,
                    outputBlob = null,
                    recordsRead = matched.size,
                    recordsMatched = matched.size,
                    recordsWritten = 0,
                    recordsInvalid = 0,
                    cancelled = true
                )
            }
            val rows = processor.processAll(matched)
            val invalid = (matched.size - rows.size).coerceAtLeast(0)
            if (dryRun || rows.isEmpty()) {
                return FilePipelineResult(
                    inputBlob = inputBlobName,
                    outputBlob = null,
                    recordsRead = matched.size,
                    recordsMatched = matched.size,
                    recordsWritten = 0,
                    recordsInvalid = invalid
                )
            }
            val bytes = writer.write(rows)
            storage.writeOutput(outputBlobName, bytes, outputMetadata)
            log.info(
                "Pipeline wrote {} row(s) {} -> {} ({} bytes)",
                rows.size,
                inputBlobName,
                outputBlobName,
                bytes.size
            )
            FilePipelineResult(
                inputBlob = inputBlobName,
                outputBlob = outputBlobName,
                recordsRead = matched.size,
                recordsMatched = matched.size,
                recordsWritten = rows.size,
                recordsInvalid = invalid
            )
        } catch (ex: Exception) {
            log.error("Pipeline failed for {}: {}", inputBlobName, ex.message, ex)
            FilePipelineResult(
                inputBlob = inputBlobName,
                outputBlob = null,
                recordsRead = 0,
                recordsMatched = 0,
                recordsWritten = 0,
                recordsInvalid = 0,
                error = ex.message ?: ex.javaClass.simpleName
            )
        }
    }

    companion object {
        fun parquetNameFor(avroBlobName: String): String {
            val trimmed = avroBlobName.trim()
            return if (trimmed.endsWith(".avro", ignoreCase = true)) {
                trimmed.dropLast(5) + ".parquet"
            } else {
                "$trimmed.parquet"
            }
        }

        /**
         * Factory used by the connector runner. Keeps pipeline types out of Spring.
         */
        fun create(
            inputBlobName: String,
            storage: ConsumptionBlobStorageClient,
            objectType: String,
            dryRun: Boolean,
            cancelRequested: () -> Boolean,
            inputPrefixes: List<String> = emptyList(),
            outputPrefix: String = "",
            outputMetadata: Map<String, String> = emptyMap()
        ): ConsumptionBlobFilePipeline =
            ConsumptionBlobFilePipeline(
                inputBlobName = inputBlobName,
                outputBlobName = ConsumptionBlobPathSupport.parquetOutputName(
                    inputBlobName,
                    inputPrefixes,
                    outputPrefix
                ),
                storage = storage,
                reader = ObjectTypeAvroRecordReader(objectType),
                processor = ConsumptionMetricRecordProcessor(sourceBlob = inputBlobName),
                writer = ParquetConsumptionMetricWriter(),
                dryRun = dryRun,
                cancelRequested = cancelRequested,
                outputMetadata = outputMetadata
            )
    }
}
