package org.jrtech.platformmanagement.dto

import java.time.Instant

data class EventHubConnectorStatusResponse(
    val id: String,
    val enabled: Boolean,
    val configured: Boolean,
    val running: Boolean,
    val autoStart: Boolean,
    val fullyQualifiedNamespace: String?,
    val eventHubName: String?,
    val consumerGroup: String,
    val requireSourceRefId: Boolean,
    val runtime: String?,
    val lastStartedBy: String?,
    val lastStartedAt: Instant?,
    val lastStoppedBy: String?,
    val lastStoppedAt: Instant?,
    val lastError: String?
)

/**
 * Run / monitor info for the Entra directory Graph loader connector (`entra-directory`).
 * Directory content remains on `/api/v1/entra/` view endpoints.
 */
data class EntraDirectoryConnectorStatusResponse(
    val id: String,
    val enabled: Boolean,
    // True when Graph client bean is available (credentials / MI configured).
    val configured: Boolean,
    val loadOnStartup: Boolean,
    val refreshIntervalMs: Long,
    val groupNamePrefix: String,
    val includeTransitiveMembers: Boolean,
    // True while a Graph refresh is holding the load lock.
    val refreshInProgress: Boolean,
    val lastLoadedAt: Instant?,
    val lastRefreshStartedAt: Instant?,
    val lastRefreshFinishedAt: Instant?,
    val lastRefreshBy: String?,
    val lastError: String?,
    val groupCount: Int,
    val memberCount: Int,
    val uniqueMemberCount: Int,
    val detail: String?
)

data class ConnectorHealthListResponse(
    val connectors: List<ConnectorHealthItemResponse>
)

data class ConnectorHealthItemResponse(
    val id: String,
    val enabled: Boolean,
    val status: String,
    val detail: String?,
    val attributes: Map<String, String>
)
