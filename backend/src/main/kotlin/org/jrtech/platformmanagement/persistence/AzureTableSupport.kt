package org.jrtech.platformmanagement.persistence

import com.azure.core.credential.TokenCredential
import com.azure.data.tables.TableClient
import com.azure.data.tables.TableServiceClient
import com.azure.data.tables.TableServiceClientBuilder
import com.azure.data.tables.models.ListEntitiesOptions
import com.azure.data.tables.models.TableEntity
import com.azure.data.tables.models.TableServiceException
import org.jrtech.platformmanagement.config.azure.AzureCredentialFactory
import org.jrtech.platformmanagement.config.azure.AzureCredentialProperties
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.logging.logger
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

object AzureTableKeys {
    const val PK_SERVICE = "service"
    const val PK_PARTICIPANT = "participant"
    const val PK_CALLER = "caller"
    const val PK_SOURCE_REF = "sourceRef"

    /**
     * Azure Table keys cannot contain `/ \ # ?`. Service offering ids may include `/`.
     */
    fun encode(raw: String): String =
        java.net.URLEncoder.encode(raw.trim(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")

    fun decode(raw: String): String =
        java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8)
}

fun TableEntity.stringProp(name: String): String? =
    getProperty(name)?.toString()?.takeIf { it.isNotBlank() }

fun TableEntity.requireString(name: String): String =
    stringProp(name) ?: error("Missing table property '$name' on ${partitionKey}/${rowKey}")

fun TableEntity.boolProp(name: String, default: Boolean = false): Boolean {
    val v = getProperty(name) ?: return default
    return when (v) {
        is Boolean -> v
        else -> v.toString().equals("true", ignoreCase = true)
    }
}

fun TableEntity.instantProp(name: String, default: Instant = UtcTimestamps.now()): Instant {
    val v = getProperty(name) ?: return default
    return when (v) {
        is Instant -> v
        is java.util.Date -> v.toInstant()
        is java.time.OffsetDateTime -> v.toInstant()
        else -> runCatching { Instant.parse(v.toString()) }.getOrDefault(default)
    }
}

fun TableEntity.localDateProp(name: String): LocalDate? {
    val raw = stringProp(name) ?: return null
    return runCatching { LocalDate.parse(raw) }.getOrNull()
}

fun TableEntity.uuidProp(name: String): UUID? {
    val raw = stringProp(name) ?: return null
    return runCatching { UUID.fromString(raw) }.getOrNull()
}

fun TableClient.listAll(): List<TableEntity> =
    listEntities(ListEntitiesOptions(), null, null).map { it }

fun TableClient.getOrNull(partitionKey: String, rowKey: String): TableEntity? =
    try {
        getEntity(partitionKey, rowKey)
    } catch (ex: TableServiceException) {
        if (ex.response?.statusCode == 404) null else throw ex
    }

fun TableClient.upsert(entity: TableEntity) {
    upsertEntity(entity)
}

fun TableClient.deleteIfExists(partitionKey: String, rowKey: String) {
    try {
        deleteEntity(partitionKey, rowKey)
    } catch (ex: TableServiceException) {
        if (ex.response?.statusCode != 404) throw ex
    }
}

class AzureTableClientFactory(
    private val properties: AzureTableProperties,
    private val azureCredential: AzureCredentialProperties
) {
    private val log = logger()

    fun createServiceClient(): TableServiceClient {
        val connectionString = properties.connectionString.trim()
        if (connectionString.isNotEmpty()) {
            log.info("Azure Table: using connection string")
            return TableServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()
        }
        val endpoint = properties.endpoint.trim()
        require(endpoint.isNotEmpty()) {
            "app.azure-table.endpoint or app.azure-table.connection-string is required when azure-table.enabled=true"
        }
        val credential: TokenCredential =
            AzureCredentialFactory.create(azureCredential, purpose = "Azure Table Storage")
        log.info("Azure Table: using TokenCredential endpoint={}", endpoint)
        return TableServiceClientBuilder()
            .endpoint(endpoint)
            .credential(credential)
            .buildClient()
    }

    fun ensureTables(service: TableServiceClient) {
        if (!properties.createTablesIfNotExist) return
        listOf(
            properties.tableName(properties.servicesTable),
            properties.tableName(properties.participantsTable),
            properties.tableName(properties.callersTable),
            properties.tableName(properties.entitlementsTable),
            properties.tableName(properties.consumptionsTable),
            properties.tableName(properties.consumptionSourceRefTable),
            properties.tableName(properties.consumptionBlobTable)
        ).forEach { name ->
            try {
                service.createTableIfNotExists(name)
                log.info("Azure Table ensured: {}", name)
            } catch (ex: Exception) {
                log.warn("Could not create table {}: {}", name, ex.message)
            }
        }
    }

    fun table(service: TableServiceClient, logicalName: String): TableClient =
        service.getTableClient(properties.tableName(logicalName))
}
