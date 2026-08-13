package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.config.ConsumptionBlobProperties
import org.jrtech.platformmanagement.connectors.consumption.blob.claim.InMemoryBlobFileClaimStore
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ConsumptionBlobFilePipelineTest
import org.jrtech.platformmanagement.connectors.consumption.blob.pipeline.ObjectTypeAvroRecordReaderTest
import org.jrtech.platformmanagement.jobs.JobExecutor
import org.jrtech.platformmanagement.dto.ConsumptionBlobImportRequest
import org.jrtech.platformmanagement.exception.BadRequestException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.beans.factory.ObjectProvider
import java.time.LocalDate

@ResourceLock("job-executor")
class ConsumptionBlobImportServiceTest {

    private val storage = ConsumptionBlobFilePipelineTest.InMemoryBlobStorage()
    private val claims = InMemoryBlobFileClaimStore()

    @BeforeEach
    fun resetStores() {
        storage.inputs.clear()
        storage.outputs.clear()
        claims.clear()
    }

    private fun service(props: ConsumptionBlobProperties = enabledProps()): ConsumptionBlobContainerConnector =
        ConsumptionBlobContainerConnector(
            connectorsProperties = ConnectorsProperties(consumptionBlob = props),
            storageClientProvider = staticProvider(storage),
            claimStore = claims
        )

    private fun enabledProps(
        maxRangeDays: Int = 31,
        maxBlobsPerJob: Int = 500,
        inputBlobPrefix: String = "",
        inputBlobPrefixes: List<String> = emptyList(),
        outputBlobPrefix: String = ""
    ) = ConsumptionBlobProperties(
        enabled = true,
        storageAccountName = "acct",
        inputContainer = "avro-in",
        outputContainer = "parquet-out",
        inputBlobPrefix = inputBlobPrefix,
        inputBlobPrefixes = inputBlobPrefixes,
        outputBlobPrefix = outputBlobPrefix,
        maxRangeDays = maxRangeDays,
        maxBlobsPerJob = maxBlobsPerJob
    )

    @Test
    fun `status reports ready when enabled and configured`() {
        val status = service().status()
        assertThat(status.enabled).isTrue()
        assertThat(status.configured).isTrue()
        assertThat(status.detail).isEqualTo("ready")
        assertThat(status.inputContainer).isEqualTo("avro-in")
        assertThat(status.outputContainer).isEqualTo("parquet-out")
        assertThat(status.objectType).isEqualTo("consumption_metric")
    }

    @Test
    fun `configure sets job dates and start runs pipelines`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["2024/07/01/14_30_00.avro"] = sampleAvro()

        val svc = service()
        svc.configure(
            mapOf(
                "startDate" to "2024-07-01",
                "endDate" to day,
                "dryRun" to true
            )
        )
        assertThat(svc.configuration()["startDate"]).isEqualTo("2024-07-01")
        assertThat(svc.configuration()["endDate"]).isEqualTo("2024-07-01")
        assertThat(svc.configuration()["dryRun"]).isEqualTo(true)

        val info = svc.start("admin@x.com")
        assertThat(info.running).isFalse()
        assertThat(info.lastStartedBy).isEqualTo("admin@x.com")
        assertThat(svc.lastImportResult()).isNotNull
        assertThat(svc.lastImportResult()!!.dryRun).isTrue()
        assertThat(svc.lastImportResult()!!.recordsMatched).isEqualTo(1)
        assertThat(svc.lastImportResult()!!.outputFiles).isEqualTo(0)
        assertThat(svc.info().logSnapshot.lineCount).isGreaterThan(0)
        assertThat(storage.outputs).isEmpty()
    }

    @Test
    fun `start without job dates is rejected`() {
        assertThatThrownBy { service().start("admin@x.com") }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("startDate")
    }

    @Test
    fun `stop when idle is idempotent`() {
        val info = service().stop("admin@x.com")
        assertThat(info.running).isFalse()
        assertThat(info.lastStoppedBy).isEqualTo("admin@x.com")
    }

    @Test
    fun `viewRange lists avro blobs for date range`() {
        val day = LocalDate.of(2024, 7, 1)
        val blobName = "2024/07/01/14_30_00.avro"
        storage.inputs[blobName] = byteArrayOf(1)

        val view = service().viewRange(day, day)
        assertThat(view.fromDate).isEqualTo(day)
        assertThat(view.untilDate).isEqualTo(day)
        assertThat(view.daysVisited).isEqualTo(1)
        assertThat(view.blobCount).isEqualTo(1)
        assertThat(view.blobs.single().name).isEqualTo(blobName)
        assertThat(view.lastImport).isNull()
    }

    @Test
    fun `viewRange rejects inverted dates`() {
        assertThatThrownBy {
            service().viewRange(LocalDate.of(2024, 7, 5), LocalDate.of(2024, 7, 1))
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("untilDate")
    }

    @Test
    fun `process rejected when disabled`() {
        val svc = service(ConsumptionBlobProperties(enabled = false))
        assertThatThrownBy {
            svc.processRange(
                ConsumptionBlobImportRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1)),
                "admin@x.com"
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("disabled")
    }

    @Test
    fun `process rejected when range too large`() {
        val svc = service(enabledProps(maxRangeDays = 2))
        assertThatThrownBy {
            svc.processRange(
                ConsumptionBlobImportRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5)),
                "admin@x.com"
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("max allowed is 2")
    }

    @Test
    fun `process rejected when end before start`() {
        val svc = service()
        assertThatThrownBy {
            svc.processRange(
                ConsumptionBlobImportRequest(LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 1)),
                "admin@x.com"
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("endDate")
    }

    @Test
    fun `processRange writes parquet for matching records`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["2024/07/01/14_30_00.avro"] = sampleAvro()

        val result = service().processRange(
            ConsumptionBlobImportRequest(day, day, dryRun = false),
            "maintainer@x.com"
        )

        assertThat(result.daysVisited).isEqualTo(1)
        assertThat(result.blobsDiscovered).isEqualTo(1)
        assertThat(result.blobsProcessed).isEqualTo(1)
        assertThat(result.recordsMatched).isEqualTo(1)
        assertThat(result.recordsWritten).isEqualTo(1)
        assertThat(result.outputFiles).isEqualTo(1)
        assertThat(result.requestedBy).isEqualTo("maintainer@x.com")
        assertThat(storage.outputs).containsKey("2024/07/01/14_30_00.parquet")
    }

    @Test
    fun `dryRun parses without writing parquet`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["2024/07/01/01_00_00.avro"] = sampleAvro()

        val result = service().processRange(
            ConsumptionBlobImportRequest(day, day, dryRun = true),
            "admin@x.com"
        )

        assertThat(result.dryRun).isTrue()
        assertThat(result.recordsMatched).isEqualTo(1)
        assertThat(result.recordsWritten).isEqualTo(0)
        assertThat(result.outputFiles).isEqualTo(0)
        assertThat(storage.outputs).isEmpty()
    }

    @Test
    fun `uses input prefix when listing and writes under output prefix`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["capture/2024/07/01/10_00_00.avro"] = sampleAvro()

        val result = service(
            enabledProps(inputBlobPrefix = "capture", outputBlobPrefix = "curated")
        ).processRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")

        assertThat(result.inputBlobPrefixes).containsExactly("capture")
        assertThat(result.outputBlobPrefix).isEqualTo("curated")
        assertThat(result.blobsDiscovered).isEqualTo(1)
        assertThat(storage.outputs).containsKey("curated/2024/07/01/10_00_00.parquet")
    }

    @Test
    fun `lists under multiple input prefixes and writes under one output prefix`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["eh-capture/2024/07/01/10_00_00.avro"] = sampleAvro()
        storage.inputs["manual/import/2024/07/01/11_00_00.avro"] = sampleAvro()

        val result = service(
            enabledProps(
                inputBlobPrefixes = listOf("eh-capture", "manual/import"),
                outputBlobPrefix = "curated"
            )
        ).processRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")

        assertThat(result.inputBlobPrefixes).containsExactly("eh-capture", "manual/import")
        assertThat(result.outputBlobPrefix).isEqualTo("curated")
        assertThat(result.blobsDiscovered).isEqualTo(2)
        assertThat(result.outputFiles).isEqualTo(2)
        assertThat(storage.outputs.keys).containsExactlyInAnyOrder(
            "curated/2024/07/01/10_00_00.parquet",
            "curated/2024/07/01/11_00_00.parquet"
        )
    }

    @Test
    fun `request inputBlobPrefixes filters to configured subset`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["b/2024/07/01/10_00_00.avro"] = sampleAvro()
        storage.inputs["a/2024/07/01/10_00_00.avro"] = sampleAvro()

        val result = service(
            enabledProps(inputBlobPrefixes = listOf("a", "b", "c"), outputBlobPrefix = "out")
        ).processRange(
            ConsumptionBlobImportRequest(day, day, inputBlobPrefixes = listOf("b")),
            "admin@x.com"
        )

        assertThat(result.inputBlobPrefixes).containsExactly("b")
        assertThat(result.blobsDiscovered).isEqualTo(1)
        assertThat(storage.outputs.keys).containsExactly("out/2024/07/01/10_00_00.parquet")
    }

    @Test
    fun `request inputBlobPrefixes rejects unknown prefix`() {
        assertThatThrownBy {
            service(enabledProps(inputBlobPrefixes = listOf("a"))).processRange(
                ConsumptionBlobImportRequest(
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 1, 1),
                    inputBlobPrefixes = listOf("unknown")
                ),
                "admin@x.com"
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("not configured")
    }

    @Test
    fun `status exposes resolved input and output prefixes`() {
        val status = service(
            enabledProps(inputBlobPrefixes = listOf("in"), outputBlobPrefix = "out")
        ).status()
        assertThat(status.inputBlobPrefixes).containsExactly("in")
        assertThat(status.outputBlobPrefix).isEqualTo("out")
    }

    @Test
    fun `health reports DISABLED when not enabled`() {
        val health = service(ConsumptionBlobProperties(enabled = false)).health()
        assertThat(health.status).isEqualTo("DISABLED")
        assertThat(health.enabled).isFalse()
    }

    @Test
    fun `second processRange skips already succeeded files`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["2024/07/01/14_30_00.avro"] = sampleAvro()
        val first = service().processRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")
        assertThat(first.blobsProcessed).isEqualTo(1)
        assertThat(first.blobsSkipped).isEqualTo(0)
        val second = service().processRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")
        assertThat(second.blobsSkipped).isEqualTo(1)
        assertThat(second.blobsProcessed).isEqualTo(0)
        assertThat(second.outputFiles).isEqualTo(0)
    }

    @Test
    fun `force reconverts a succeeded file`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["2024/07/01/14_30_00.avro"] = sampleAvro()
        service().processRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")
        storage.outputs.clear()
        val forced = service().processRange(
            ConsumptionBlobImportRequest(day, day, force = true),
            "admin@x.com"
        )
        assertThat(forced.blobsProcessed).isEqualTo(1)
        assertThat(forced.blobsSkipped).isEqualTo(0)
        assertThat(storage.outputs).isNotEmpty()
    }

    @Test
    fun `processRange reuses the process-wide executor`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["2024/07/01/10_00_00.avro"] = sampleAvro()
        service().processRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")
        assertThat(JobExecutor.isRunning()).isTrue()
        val sizeAfterFirst = JobExecutor.poolSize()
        service().processRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")
        assertThat(JobExecutor.isRunning()).isTrue()
        assertThat(JobExecutor.poolSize()).isEqualTo(sizeAfterFirst)
    }

    @Test
    fun `two avro files run as separate pipelines`() {
        val day = LocalDate.of(2024, 7, 1)
        storage.inputs["2024/07/01/10_00_00.avro"] = sampleAvro()
        storage.inputs["2024/07/01/11_00_00.avro"] = sampleAvro()

        val result = service().processRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")
        assertThat(result.blobsProcessed).isEqualTo(2)
        assertThat(result.outputFiles).isEqualTo(2)
        assertThat(storage.outputs).hasSize(2)
    }

    private fun sampleAvro(): ByteArray {
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
            put("callerId", "c@x.com")
            put("serviceUrl", "https://llm.example")
            put("timestamp", "2024-07-01T14:30:00Z")
            put("objectType", "consumption_metric")
            put("usage", """{"inputToken":3,"outputToken":1}""")
        }
        return ObjectTypeAvroRecordReaderTest.writeAvro(schema, listOf(record))
    }

    private fun staticProvider(client: ConsumptionBlobStorageClient): ObjectProvider<ConsumptionBlobStorageClient> =
        object : ObjectProvider<ConsumptionBlobStorageClient> {
            override fun getObject(): ConsumptionBlobStorageClient = client
            override fun getObject(vararg args: Any?): ConsumptionBlobStorageClient = client
            override fun getIfAvailable(): ConsumptionBlobStorageClient = client
            override fun getIfUnique(): ConsumptionBlobStorageClient = client
        }
}
