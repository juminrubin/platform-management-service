package org.jrtech.platformmanagement.connectors.consumption.aggregate

import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.config.ConsumptionBlobProperties
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.InMemoryBlobFileClaimStore
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ConsumptionBlobFilePipelineTest
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ConsumptionMetricRow
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ParquetConsumptionMetricWriter
import org.jrtech.platformmanagement.exception.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.TaskScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ScheduledFuture

@ResourceLock("job-executor")
class DailyConsumptionAggregateConnectorTest {

    private val storage = ConsumptionBlobFilePipelineTest.InMemoryBlobStorage()
    private val claims = InMemoryBlobFileClaimStore()
    private val taskScheduler = mock<TaskScheduler>()
    private val scheduledFuture = mock<ScheduledFuture<*>>()

    init {
        whenever(taskScheduler.schedule(any(Runnable::class.java), any(Instant::class.java)))
            .thenReturn(scheduledFuture)
    }

    @AfterEach
    fun reset() {
        storage.outputs.clear()
        claims.clear()
    }

    @Test
    fun `info is disabled when property off`() {
        val c = connector(DailyConsumptionAggregateProperties(enabled = false))
        assertThat(c.info().id).isEqualTo("daily-consumption-aggregate")
        assertThat(c.info().status).isEqualTo("DISABLED")
        assertThat(c.isEnabled()).isFalse()
    }

    @Test
    fun `start disabled throws`() {
        val c = connector(DailyConsumptionAggregateProperties(enabled = false))
        assertThatThrownBy { c.start("x") }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("disabled")
    }

    @Test
    fun `aggregates yesterday and skips the second run`() {
        val day = LocalDate.of(2024, 7, 1)
        val writer = ParquetConsumptionMetricWriter()
        storage.outputs["curated/2024/07/01/10_00_00.parquet"] = writer.write(
            listOf(
                ConsumptionMetricRow(
                    callerId = "c",
                    serviceUrl = "https://s",
                    timestampMillis = 1,
                    objectType = "consumption_metric",
                    usageJson = "{}",
                    sourceBlob = "a.avro"
                )
            )
        )
        val c = connector(
            DailyConsumptionAggregateProperties(enabled = true, autoStart = false),
            blob = enabledBlob()
        )
        c.configure(mapOf("targetDate" to day.toString()))
        val first = c.start("admin@x.com")
        assertThat(first.running).isTrue()
        assertThat(c.lastResult()!!.sourceFiles).isEqualTo(1)
        assertThat(c.lastResult()!!.outputBlob).isEqualTo("curated/2024/07/01.parquet")
        assertThat(storage.outputs).containsKey("curated/2024/07/01.parquet")
        assertThat(c.info().configuration["sourceBlobPrefix"]).isEqualTo("curated")
        assertThat(c.info().configuration["outputBlobPrefix"]).isEqualTo("curated")

        c.configure(mapOf("targetDate" to day.toString()))
        c.start("admin@x.com")
        assertThat(c.lastResult()!!.skipped).isTrue()
        assertThat(c.lastResult()!!.skipReason).isEqualTo("already-succeeded")
    }

    @Test
    fun `writes daily parquet under configured output prefix`() {
        val day = LocalDate.of(2024, 7, 1)
        val writer = ParquetConsumptionMetricWriter()
        storage.outputs["curated/2024/07/01/10_00_00.parquet"] = writer.write(
            listOf(
                ConsumptionMetricRow(
                    callerId = "c",
                    serviceUrl = "https://s",
                    timestampMillis = 1,
                    objectType = "consumption_metric",
                    usageJson = "{}",
                    sourceBlob = "a.avro"
                )
            )
        )
        val c = connector(
            DailyConsumptionAggregateProperties(
                enabled = true,
                autoStart = false,
                outputBlobPrefix = "daily"
            ),
            blob = enabledBlob()
        )
        c.configure(mapOf("targetDate" to day.toString()))
        c.start("admin@x.com")
        assertThat(c.lastResult()!!.outputBlob).isEqualTo("daily/2024/07/01.parquet")
        assertThat(storage.outputs).containsKey("daily/2024/07/01.parquet")
        assertThat(storage.outputs).doesNotContainKey("curated/2024/07/01.parquet")
        assertThat(c.info().configuration["sourceBlobPrefix"]).isEqualTo("curated")
        assertThat(c.info().configuration["outputBlobPrefix"]).isEqualTo("daily")
    }

    @Test
    fun `yesterdayUtc is previous UTC calendar day`() {
        val noon = Instant.parse("2024-07-02T12:00:00Z")
        assertThat(DailyConsumptionAggregateConnector.yesterdayUtc(noon))
            .isEqualTo(LocalDate.of(2024, 7, 1))
    }

    @Test
    fun `nextRunInstant is the next UTC hour today or tomorrow`() {
        val before = Instant.parse("2024-07-02T00:30:00Z")
        assertThat(DailyConsumptionAggregateConnector.nextRunInstant(before, 1))
            .isEqualTo(Instant.parse("2024-07-02T01:00:00Z"))
        val after = Instant.parse("2024-07-02T02:00:00Z")
        assertThat(DailyConsumptionAggregateConnector.nextRunInstant(after, 1))
            .isEqualTo(Instant.parse("2024-07-03T01:00:00Z"))
    }

    private fun connector(
        props: DailyConsumptionAggregateProperties,
        blob: ConsumptionBlobProperties = ConsumptionBlobProperties()
    ) = DailyConsumptionAggregateConnector(
        properties = props,
        connectorsProperties = ConnectorsProperties(consumptionBlob = blob),
        storageClientProvider = staticProvider(storage),
        claimStore = claims,
        taskScheduler = taskScheduler
    )

    private fun enabledBlob() = ConsumptionBlobProperties(
        enabled = true,
        storageAccountName = "acct",
        inputContainer = "in",
        outputContainer = "out",
        outputBlobPrefix = "curated"
    )

    private fun staticProvider(
        client: org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobStorageClient
    ): ObjectProvider<org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobStorageClient> =
        object : ObjectProvider<org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobStorageClient> {
            override fun getObject() = client
            override fun getObject(vararg args: Any?) = client
            override fun getIfAvailable() = client
            override fun getIfUnique() = client
        }
}
