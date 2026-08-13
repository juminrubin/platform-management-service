package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParquetConsumptionMetricWriterTest {

    @Test
    fun `writes parquet magic bytes`() {
        val bytes = ParquetConsumptionMetricWriter().write(
            listOf(
                ConsumptionMetricRow(
                    callerId = "c",
                    serviceUrl = "https://llm",
                    timestampMillis = 1_720_000_000_000L,
                    objectType = "consumption_metric",
                    usageJson = """{"inputToken":1}""",
                    inputToken = 1,
                    outputToken = null,
                    audioLengthSeconds = null,
                    sourceBlob = "a.avro"
                )
            )
        )
        assertThat(bytes.size).isGreaterThan(8)
        assertThat(bytes.copyOfRange(0, 4)).isEqualTo("PAR1".toByteArray(Charsets.US_ASCII))
        assertThat(bytes.copyOfRange(bytes.size - 4, bytes.size))
            .isEqualTo("PAR1".toByteArray(Charsets.US_ASCII))
    }
}
