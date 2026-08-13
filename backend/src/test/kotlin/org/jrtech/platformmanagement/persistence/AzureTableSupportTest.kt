package org.jrtech.platformmanagement.persistence

import com.azure.core.http.HttpResponse
import com.azure.data.tables.TableClient
import com.azure.data.tables.TableServiceClient
import com.azure.data.tables.models.TableEntity
import com.azure.data.tables.models.TableServiceException
import org.jrtech.platformmanagement.config.azure.AzureCredentialProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID

class AzureTableSupportTest {

    @Test
    fun `TableEntity property helpers cover types and defaults`() {
        val e = TableEntity("pk", "rk")
        e.addProperty("s", "hello")
        e.addProperty("blank", "   ")
        e.addProperty("bTrue", true)
        e.addProperty("bStr", "true")
        e.addProperty("bFalse", "no")
        e.addProperty("inst", Instant.parse("2026-01-02T03:04:05Z"))
        e.addProperty("date", Date.from(Instant.parse("2026-02-01T00:00:00Z")))
        e.addProperty("odt", OffsetDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC))
        e.addProperty("instStr", "2026-04-01T00:00:00Z")
        e.addProperty("badInst", "not-a-date")
        e.addProperty("d", "2026-05-06")
        e.addProperty("badDate", "xx")
        e.addProperty("u", "11111111-1111-1111-1111-111111111111")
        e.addProperty("badUuid", "nope")

        assertThat(e.stringProp("s")).isEqualTo("hello")
        assertThat(e.stringProp("missing")).isNull()
        assertThat(e.stringProp("blank")).isNull()
        assertThat(e.requireString("s")).isEqualTo("hello")
        assertThatThrownBy { e.requireString("missing") }.isInstanceOf(IllegalStateException::class.java)

        assertThat(e.boolProp("bTrue")).isTrue()
        assertThat(e.boolProp("bStr")).isTrue()
        assertThat(e.boolProp("bFalse")).isFalse()
        assertThat(e.boolProp("absent", default = true)).isTrue()

        assertThat(e.instantProp("inst")).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"))
        assertThat(e.instantProp("date")).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"))
        assertThat(e.instantProp("odt")).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"))
        assertThat(e.instantProp("instStr")).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"))
        val fallback = Instant.parse("2020-01-01T00:00:00Z")
        assertThat(e.instantProp("badInst", default = fallback)).isEqualTo(fallback)
        assertThat(e.instantProp("absent", default = fallback)).isEqualTo(fallback)

        assertThat(e.localDateProp("d")).isEqualTo(LocalDate.of(2026, 5, 6))
        assertThat(e.localDateProp("badDate")).isNull()
        assertThat(e.localDateProp("absent")).isNull()

        assertThat(e.uuidProp("u")).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        assertThat(e.uuidProp("badUuid")).isNull()
        assertThat(e.uuidProp("absent")).isNull()
    }

    @Test
    fun `getOrNull returns null on 404 and rethrows other errors`() {
        val client = mock<TableClient>()
        val notFound = tableServiceException(404)
        val other = tableServiceException(500)
        whenever(client.getEntity("pk", "rk")).thenThrow(notFound)
        assertThat(client.getOrNull("pk", "rk")).isNull()

        whenever(client.getEntity("pk", "bad")).thenThrow(other)
        assertThatThrownBy { client.getOrNull("pk", "bad") }.isSameAs(other)
    }

    @Test
    fun `deleteIfExists swallows 404 and rethrows other errors`() {
        val client = mock<TableClient>()
        val notFound = tableServiceException(404)
        val boom = tableServiceException(503)
        whenever(client.deleteEntity("pk", "rk")).thenThrow(notFound)
        client.deleteIfExists("pk", "rk")

        whenever(client.deleteEntity("pk", "x")).thenThrow(boom)
        assertThatThrownBy { client.deleteIfExists("pk", "x") }.isSameAs(boom)
    }

    @Test
    fun `upsert delegates to upsertEntity`() {
        val client = mock<TableClient>()
        val entity = TableEntity("a", "b")
        client.upsert(entity)
        verify(client).upsertEntity(entity)
    }

    @Test
    fun `AzureTableProperties tableName and isConfigured`() {
        val empty = AzureTableProperties()
        assertThat(empty.isConfigured()).isFalse()
        assertThat(empty.tableName("services")).isEqualTo("pmsservices")
        assertThat(empty.tableName(empty.consumptionBlobTable)).isEqualTo("pmsconsumptionblob")

        val noPrefix = AzureTableProperties(tablePrefix = "  ", endpoint = "https://x.table.core.windows.net")
        assertThat(noPrefix.tableName("services")).isEqualTo("services")
        assertThat(noPrefix.isConfigured()).isTrue()

        val cs = AzureTableProperties(connectionString = "UseDevelopmentStorage=true")
        assertThat(cs.isConfigured()).isTrue()
    }

    @Test
    fun `AzureTableClientFactory createServiceClient requires endpoint or connection string`() {
        val factory = AzureTableClientFactory(
            AzureTableProperties(enabled = true, endpoint = "", connectionString = ""),
            AzureCredentialProperties()
        )
        assertThatThrownBy { factory.createServiceClient() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("endpoint")
    }

    @Test
    fun `AzureTableClientFactory ensureTables no-ops when create disabled`() {
        val factory = AzureTableClientFactory(
            AzureTableProperties(createTablesIfNotExist = false),
            AzureCredentialProperties()
        )
        val service = mock<TableServiceClient>()
        factory.ensureTables(service)
        verify(service, never()).createTableIfNotExists(any())
    }

    @Test
    fun `AzureTableClientFactory ensureTables creates and swallows failures`() {
        val factory = AzureTableClientFactory(
            AzureTableProperties(createTablesIfNotExist = true, tablePrefix = "t"),
            AzureCredentialProperties()
        )
        val service = mock<TableServiceClient>()
        whenever(service.createTableIfNotExists(any())).thenThrow(RuntimeException("denied"))
        factory.ensureTables(service)
        verify(service, org.mockito.kotlin.atLeastOnce()).createTableIfNotExists(any())
    }

    @Test
    fun `AzureTableClientFactory table resolves prefixed name`() {
        val factory = AzureTableClientFactory(
            AzureTableProperties(tablePrefix = "pms"),
            AzureCredentialProperties()
        )
        val service = mock<TableServiceClient>()
        val table = mock<TableClient>()
        whenever(service.getTableClient("pmsservices")).thenReturn(table)
        assertThat(factory.table(service, "services")).isSameAs(table)
    }

    @Test
    fun `AzureTableClientFactory connection string path builds client`() {
        val cs =
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
                "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;" +
                "TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;"
        val factory = AzureTableClientFactory(
            AzureTableProperties(connectionString = cs),
            AzureCredentialProperties()
        )
        val client = factory.createServiceClient()
        assertThat(client).isNotNull
    }

    private fun tableServiceException(status: Int): TableServiceException {
        val response = mock<HttpResponse>()
        org.mockito.Mockito.lenient().whenever(response.statusCode).thenReturn(status)
        return TableServiceException("err", response, null)
    }
}
