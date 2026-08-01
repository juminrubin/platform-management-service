package org.jrtech.platformmanagement.connectors.consumption.blob

import org.jrtech.platformmanagement.connectors.config.ConnectorsProperties
import org.jrtech.platformmanagement.connectors.config.ConsumptionBlobProperties
import org.jrtech.platformmanagement.dto.ConsumptionBlobImportRequest
import org.jrtech.platformmanagement.dto.ConsumptionResponse
import org.jrtech.platformmanagement.dto.CreateConsumptionRequest
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.service.ConsumptionService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ConsumptionBlobImportServiceTest {

    private val storage = mock<ConsumptionBlobStorageClient>()
    private val storageProvider = mock<ObjectProvider<ConsumptionBlobStorageClient>>()
    private val avroReader = mock<ConsumptionAvroFileReader>()
    private val consumptionService = mock<ConsumptionService>()

    private fun service(props: ConsumptionBlobProperties = enabledProps()): ConsumptionBlobImportService {
        whenever(storageProvider.getIfAvailable()).thenReturn(storage)
        return ConsumptionBlobImportService(
            connectorsProperties = ConnectorsProperties(consumptionBlob = props),
            storageClientProvider = storageProvider,
            avroFileReader = avroReader,
            consumptionService = consumptionService
        )
    }

    private fun enabledProps(
        maxRangeDays: Int = 31,
        maxBlobsPerJob: Int = 500,
        blobPrefix: String = "",
        blobPrefixes: List<String> = emptyList()
    ) = ConsumptionBlobProperties(
        enabled = true,
        storageAccountUrl = "https://acct.blob.core.windows.net",
        container = "consumption",
        blobPrefix = blobPrefix,
        blobPrefixes = blobPrefixes,
        maxRangeDays = maxRangeDays,
        maxBlobsPerJob = maxBlobsPerJob,
        requireSourceRefId = true
    )

    @Test
    fun `status reports ready when enabled and configured`() {
        val status = service().status()
        assertThat(status.enabled).isTrue()
        assertThat(status.configured).isTrue()
        assertThat(status.detail).isEqualTo("ready")
        assertThat(status.container).isEqualTo("consumption")
    }

    @Test
    fun `import rejected when disabled`() {
        val svc = service(ConsumptionBlobProperties(enabled = false))
        assertThatThrownBy {
            svc.importRange(
                ConsumptionBlobImportRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1)),
                "admin@x.com"
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("disabled")
    }

    @Test
    fun `import rejected when range too large`() {
        val svc = service(enabledProps(maxRangeDays = 2))
        assertThatThrownBy {
            svc.importRange(
                ConsumptionBlobImportRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5)),
                "admin@x.com"
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("max allowed is 2")
    }

    @Test
    fun `import rejected when end before start`() {
        val svc = service()
        assertThatThrownBy {
            svc.importRange(
                ConsumptionBlobImportRequest(LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 1)),
                "admin@x.com"
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("endDate")
    }

    @Test
    fun `import lists day prefixes and imports records`() {
        val day = LocalDate.of(2024, 7, 1)
        val blobName = "2024/07/01/14_30_00.avro"
        whenever(storage.listAvroBlobs("2024/07/01/"))
            .thenReturn(listOf(BlobObjectRef(blobName, 100)))
        whenever(storage.openBlob(blobName)).thenReturn(ByteArrayInputStream(byteArrayOf(1)))

        val parsed = ConsumptionAvroFileReader.AvroRecordResult(
            request = CreateConsumptionRequest(
                callerId = "c@x.com",
                serviceOfferingId = "svc",
                sourceRefId = "ref-1",
                consumptionData = "{}",
                capturedAt = Instant.parse("2024-07-01T14:30:00Z")
            ),
            externalId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        )
        whenever(avroReader.readAll(any(), eq(true))).thenReturn(listOf(parsed))
        whenever(consumptionService.createFromImport(any(), anyOrNull())).thenReturn(
            ConsumptionService.ImportCreateResult(
                created = true,
                response = sampleResponse()
            )
        )

        val result = service().importRange(
            ConsumptionBlobImportRequest(day, day, dryRun = false),
            "maintainer@x.com"
        )

        assertThat(result.daysVisited).isEqualTo(1)
        assertThat(result.blobsDiscovered).isEqualTo(1)
        assertThat(result.blobsProcessed).isEqualTo(1)
        assertThat(result.rowsParsed).isEqualTo(1)
        assertThat(result.rowsInserted).isEqualTo(1)
        assertThat(result.rowsDuplicate).isEqualTo(0)
        assertThat(result.requestedBy).isEqualTo("maintainer@x.com")
        verify(consumptionService, times(1)).createFromImport(any(), anyOrNull())
    }

    @Test
    fun `dryRun parses without writing`() {
        val day = LocalDate.of(2024, 7, 1)
        whenever(storage.listAvroBlobs(any())).thenReturn(listOf(BlobObjectRef("2024/07/01/01_00_00.avro")))
        whenever(storage.openBlob(any())).thenReturn(ByteArrayInputStream(byteArrayOf(1)))
        whenever(avroReader.readAll(any(), any())).thenReturn(
            listOf(
                ConsumptionAvroFileReader.AvroRecordResult(
                    request = CreateConsumptionRequest("c", "s", "r"),
                    externalId = null
                )
            )
        )

        val result = service().importRange(
            ConsumptionBlobImportRequest(day, day, dryRun = true),
            "admin@x.com"
        )

        assertThat(result.dryRun).isTrue()
        assertThat(result.rowsParsed).isEqualTo(1)
        assertThat(result.rowsInserted).isEqualTo(0)
        verify(consumptionService, never()).createFromImport(any(), anyOrNull())
    }

    @Test
    fun `uses blob prefix when listing`() {
        val day = LocalDate.of(2024, 7, 1)
        whenever(storage.listAvroBlobs("capture/2024/07/01/")).thenReturn(emptyList())

        val result = service(enabledProps(blobPrefix = "capture")).importRange(
            ConsumptionBlobImportRequest(day, day),
            "admin@x.com"
        )

        assertThat(result.blobsDiscovered).isEqualTo(0)
        assertThat(result.blobPrefixes).containsExactly("capture")
        verify(storage).listAvroBlobs("capture/2024/07/01/")
    }

    @Test
    fun `lists under multiple configured blob prefixes`() {
        val day = LocalDate.of(2024, 7, 1)
        whenever(storage.listAvroBlobs("eh-capture/2024/07/01/"))
            .thenReturn(listOf(BlobObjectRef("eh-capture/2024/07/01/10_00_00.avro")))
        whenever(storage.listAvroBlobs("manual/import/2024/07/01/"))
            .thenReturn(listOf(BlobObjectRef("manual/import/2024/07/01/11_00_00.avro")))
        whenever(storage.openBlob(any())).thenReturn(ByteArrayInputStream(byteArrayOf(1)))
        whenever(avroReader.readAll(any(), any())).thenReturn(emptyList())

        val result = service(
            enabledProps(blobPrefixes = listOf("eh-capture", "manual/import"))
        ).importRange(ConsumptionBlobImportRequest(day, day), "admin@x.com")

        assertThat(result.blobPrefixes).containsExactly("eh-capture", "manual/import")
        assertThat(result.blobsDiscovered).isEqualTo(2)
        assertThat(result.blobsProcessed).isEqualTo(2)
        verify(storage).listAvroBlobs("eh-capture/2024/07/01/")
        verify(storage).listAvroBlobs("manual/import/2024/07/01/")
    }

    @Test
    fun `request blobPrefixes filters to configured subset`() {
        val day = LocalDate.of(2024, 7, 1)
        whenever(storage.listAvroBlobs("b/2024/07/01/")).thenReturn(emptyList())

        val result = service(
            enabledProps(blobPrefixes = listOf("a", "b", "c"))
        ).importRange(
            ConsumptionBlobImportRequest(day, day, blobPrefixes = listOf("b")),
            "admin@x.com"
        )

        assertThat(result.blobPrefixes).containsExactly("b")
        verify(storage).listAvroBlobs("b/2024/07/01/")
        verify(storage, never()).listAvroBlobs(eq("a/2024/07/01/"))
        verify(storage, never()).listAvroBlobs(eq("c/2024/07/01/"))
    }

    @Test
    fun `request blobPrefixes rejects unknown prefix`() {
        assertThatThrownBy {
            service(enabledProps(blobPrefixes = listOf("a"))).importRange(
                ConsumptionBlobImportRequest(
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 1, 1),
                    blobPrefixes = listOf("unknown")
                ),
                "admin@x.com"
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("not configured")
    }

    @Test
    fun `status exposes resolved blobPrefixes`() {
        val status = service(enabledProps(blobPrefixes = listOf("x", "y"))).status()
        assertThat(status.blobPrefixes).containsExactly("x", "y")
        @Suppress("DEPRECATION")
        assertThat(status.blobPrefix).isEqualTo("x")
    }

    @Test
    fun `health reports DISABLED when not enabled`() {
        val health = service(ConsumptionBlobProperties(enabled = false)).health()
        assertThat(health.status).isEqualTo("DISABLED")
        assertThat(health.enabled).isFalse()
    }

    private fun sampleResponse() = ConsumptionResponse(
        id = UUID.randomUUID(),
        callerId = "c@x.com",
        participantId = "p1",
        participantName = "P",
        serviceOfferingId = "svc",
        serviceOfferingName = "Svc",
        sourceRefId = "ref-1",
        consumptionData = "{}",
        capturedAt = Instant.parse("2024-07-01T14:30:00Z"),
        createdAt = Instant.parse("2024-07-01T14:31:00Z")
    )
}
