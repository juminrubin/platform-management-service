package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

/** Outcome of one [ConsumptionBlobFilePipeline] (one input Avro file). */
data class FilePipelineResult(
    val inputBlob: String,
    val outputBlob: String?,
    val recordsRead: Int,
    val recordsMatched: Int,
    val recordsWritten: Int,
    val recordsInvalid: Int,
    val cancelled: Boolean = false,
    val skipped: Boolean = false,
    val skipReason: String? = null,
    val error: String? = null
) {
    companion object {
        fun skipped(inputBlob: String, reason: String): FilePipelineResult =
            FilePipelineResult(
                inputBlob = inputBlob,
                outputBlob = null,
                recordsRead = 0,
                recordsMatched = 0,
                recordsWritten = 0,
                recordsInvalid = 0,
                skipped = true,
                skipReason = reason
            )
    }
}
