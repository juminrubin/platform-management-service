package org.jrtech.platformmanagement.bootstrap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

/**
 * Root document for [classpath:datasource.json] (and custom locations).
 *
 * Unknown fields (e.g. service `tags`, caller `label`) are ignored so the file
 * can carry extra metadata without schema coupling.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DataSourceDocument(
    val services: List<DataSourceService> = emptyList(),
    val participants: List<DataSourceParticipant> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataSourceService(
    val id: String,
    val name: String,
    val category: String,
    val description: String? = null,
    val provider: String? = null,
    val config: String? = null,
    val active: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataSourceParticipant(
    val id: String,
    val name: String,
    val contact: String? = null,
    val status: String = "ACTIVE",
    val callers: List<DataSourceCaller> = emptyList(),
    val entitlements: List<DataSourceEntitlement> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataSourceCaller(
    val id: String,
    val status: String = "ACTIVE"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataSourceEntitlement(
    val serviceId: String,
    val status: String = "ACTIVE",
    val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    val config: String? = null,
    val notes: String? = null
)
