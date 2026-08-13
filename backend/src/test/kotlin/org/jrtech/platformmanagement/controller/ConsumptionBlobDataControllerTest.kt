package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.connectors.consumption.blob.ConsumptionBlobContainerConnector
import org.jrtech.platformmanagement.dto.ConsumptionBlobObjectView
import org.jrtech.platformmanagement.dto.ConsumptionBlobViewResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZoneOffset

class ConsumptionBlobDataControllerTest {

    private val blobConnector = mock<ConsumptionBlobContainerConnector>()
    private val controller = ConsumptionBlobDataController(blobConnector)

    @Test
    fun `viewBlobs defaults both dates to today UTC`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val response = ConsumptionBlobViewResponse(
            fromDate = today,
            untilDate = today,
            inputBlobPrefixes = listOf(""),
            daysVisited = 1,
            blobCount = 0,
            blobs = emptyList()
        )
        whenever(blobConnector.viewRange(today, today)).thenReturn(response)

        val result = controller.viewBlobs(fromDate = null, untilDate = null)
        assertThat(result.fromDate).isEqualTo(today)
        assertThat(result.untilDate).isEqualTo(today)
        verify(blobConnector).viewRange(eq(today), eq(today))
    }

    @Test
    fun `viewBlobs passes explicit fromDate and untilDate`() {
        val from = LocalDate.of(2024, 7, 1)
        val until = LocalDate.of(2024, 7, 3)
        val response = ConsumptionBlobViewResponse(
            fromDate = from,
            untilDate = until,
            inputBlobPrefixes = listOf("capture"),
            daysVisited = 3,
            blobCount = 1,
            blobs = listOf(ConsumptionBlobObjectView("capture/2024/07/01/14_30_00.avro", 100))
        )
        whenever(blobConnector.viewRange(from, until)).thenReturn(response)

        val result = controller.viewBlobs(fromDate = from, untilDate = until)
        assertThat(result.blobCount).isEqualTo(1)
        assertThat(result.blobs.single().name).contains("14_30_00.avro")
        verify(blobConnector).viewRange(from, until)
    }

    @Test
    fun `viewBlobs rejects untilDate before fromDate`() {
        assertThatThrownBy {
            controller.viewBlobs(
                fromDate = LocalDate.of(2024, 7, 5),
                untilDate = LocalDate.of(2024, 7, 1)
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("untilDate")
    }
}
