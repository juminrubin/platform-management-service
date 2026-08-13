package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ConsumptionMetricRecordProcessorTest {

    private val processor = ConsumptionMetricRecordProcessor(sourceBlob = "2024/07/01/14_30_00.avro")

    @Test
    fun `flattens LLM usage tokens`() {
        val row = processor.process(
            metric(
                usage = """{"inputToken":100,"outputToken":20}""",
                serviceUrl = "https://llm.example/chat"
            )
        )!!
        assertThat(row.inputToken).isEqualTo(100)
        assertThat(row.outputToken).isEqualTo(20)
        assertThat(row.audioLengthSeconds).isNull()
        assertThat(row.sourceBlob).endsWith(".avro")
        assertThat(row.usageJson).contains("inputToken")
    }

    @Test
    fun `flattens embedding inputToken only`() {
        val row = processor.process(metric(usage = """{"inputToken":32}"""))!!
        assertThat(row.inputToken).isEqualTo(32)
        assertThat(row.outputToken).isNull()
        assertThat(row.audioLengthSeconds).isNull()
    }

    @Test
    fun `flattens STT audioLength seconds`() {
        val row = processor.process(metric(usage = """{"audioLength": 3.5}"""))!!
        assertThat(row.audioLengthSeconds).isEqualTo(3.5)
        assertThat(row.inputToken).isNull()
        assertThat(row.outputToken).isNull()
    }

    @Test
    fun `accepts snake_case usage keys`() {
        val row = processor.process(
            metric(usage = """{"input_token":1,"output_token":2,"audio_length":0.25}""")
        )!!
        assertThat(row.inputToken).isEqualTo(1)
        assertThat(row.outputToken).isEqualTo(2)
        assertThat(row.audioLengthSeconds).isEqualTo(0.25)
    }

    private fun metric(
        usage: String,
        serviceUrl: String = "https://svc.example"
    ) = ConsumptionMetricRecord(
        callerId = "caller-1",
        serviceUrl = serviceUrl,
        timestamp = Instant.parse("2024-07-01T14:30:00Z"),
        objectType = "consumption_metric",
        usageJson = usage
    )
}
