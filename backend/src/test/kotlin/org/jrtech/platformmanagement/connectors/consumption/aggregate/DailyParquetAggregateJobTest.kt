package org.jrtech.platformmanagement.connectors.consumption.aggregate

import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ConsumptionBlobFilePipelineTest
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ConsumptionMetricRow
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ParquetConsumptionMetricWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyParquetAggregateJobTest {

    @Test
    fun `merges five-minute parquet into one daily file`() {
        val storage = ConsumptionBlobFilePipelineTest.InMemoryBlobStorage()
        val writer = ParquetConsumptionMetricWriter()
        val day = LocalDate.of(2024, 7, 1)
        storage.outputs["curated/2024/07/01/10_00_00.parquet"] = writer.write(
            listOf(row("a", 1_000L, "first.avro"))
        )
        storage.outputs["curated/2024/07/01/10_05_00.parquet"] = writer.write(
            listOf(row("b", 2_000L, "second.avro"))
        )
        storage.outputs["curated/2024/07/01.parquet"] = byteArrayOf(1, 2, 3)

        val result = DailyParquetAggregateJob(
            day = day,
            storage = storage,
            sourcePrefix = "curated",
            outputPrefix = "curated"
        ).run()

        assertThat(result.sourceFiles).isEqualTo(2)
        assertThat(result.rowsWritten).isEqualTo(2)
        assertThat(result.outputBlob).isEqualTo("curated/2024/07/01.parquet")
        assertThat(storage.outputs["curated/2024/07/01.parquet"]!!.size).isGreaterThan(8)
    }

    @Test
    fun `writes daily file under a different prefix than the five-minute sources`() {
        val storage = ConsumptionBlobFilePipelineTest.InMemoryBlobStorage()
        val writer = ParquetConsumptionMetricWriter()
        val day = LocalDate.of(2024, 7, 1)
        storage.outputs["landing/2024/07/01/10_00_00.parquet"] = writer.write(
            listOf(row("a", 1_000L, "first.avro"))
        )
        storage.outputs["landing/2024/07/01/10_05_00.parquet"] = writer.write(
            listOf(row("b", 2_000L, "second.avro"))
        )

        val result = DailyParquetAggregateJob(
            day = day,
            storage = storage,
            sourcePrefix = "landing",
            outputPrefix = "daily"
        ).run()

        assertThat(result.sourceFiles).isEqualTo(2)
        assertThat(result.rowsWritten).isEqualTo(2)
        assertThat(result.outputBlob).isEqualTo("daily/2024/07/01.parquet")
        assertThat(storage.outputs).containsKey("daily/2024/07/01.parquet")
        assertThat(storage.outputs).doesNotContainKey("landing/2024/07/01.parquet")
    }

    @Test
    fun `empty day is a no-op`() {
        val storage = ConsumptionBlobFilePipelineTest.InMemoryBlobStorage()
        val result = DailyParquetAggregateJob(
            day = LocalDate.of(2024, 7, 2),
            storage = storage,
            sourcePrefix = "curated",
            outputPrefix = "curated"
        ).run()
        assertThat(result.sourceFiles).isEqualTo(0)
        assertThat(result.outputBlob).isNull()
        assertThat(storage.outputs).isEmpty()
    }

    private fun row(caller: String, ts: Long, source: String) = ConsumptionMetricRow(
        callerId = caller,
        serviceUrl = "https://svc",
        timestampMillis = ts,
        objectType = "consumption_metric",
        usageJson = """{"inputToken":1}""",
        inputToken = 1,
        sourceBlob = source
    )
}
