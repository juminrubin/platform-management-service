package org.jrtech.platformmanagement.dto

import java.time.Instant

/**
 * Summary row for `GET /api/v1/connectors` (no log body).
 */
data class ConnectorSummaryResponse(
    val id: String,
    val enabled: Boolean,
    val configured: Boolean,
    val running: Boolean,
    /** UP | DOWN | DEGRADED | DISABLED | STOPPED | RUNNING */
    val status: String,
    val detail: String?,
    val attributes: Map<String, String> = emptyMap()
)

data class ConnectorListResponse(
    val connectors: List<ConnectorSummaryResponse>
)

/**
 * Full connector process view for GET /api/v1/connectors/{id}.
 *
 * - configuration: public (non-secret) settings that control what the process does
 * - attributes: operational counters / last-run metadata
 * - logSnapshot: recent process log lines, total UTF-8 size capped (default 32 KiB)
 *
 * Domain data produced by the connector is not included here; use data-plane APIs
 * such as /api/v1/entra/groups, /api/v1/consumption/blob, /api/v1/consumptions.
 */
data class ConnectorInfoResponse(
    val id: String,
    val enabled: Boolean,
    val configured: Boolean,
    val running: Boolean,
    val status: String,
    val detail: String?,
    val lastStartedBy: String? = null,
    val lastStartedAt: Instant? = null,
    val lastStoppedBy: String? = null,
    val lastStoppedAt: Instant? = null,
    val lastError: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val configuration: Map<String, Any?> = emptyMap(),
    val logSnapshot: ConnectorLogSnapshotResponse = ConnectorLogSnapshotResponse()
)

/**
 * In-memory process log window exposed on connector info.
 * [bytes] is the UTF-8 size of the retained lines (including newlines between them).
 * [lines] are in **descending** order: index 0 is the newest entry.
 */
data class ConnectorLogSnapshotResponse(
    val maxBytes: Int = 32 * 1024,
    val bytes: Int = 0,
    val lineCount: Int = 0,
    /** Newest first (index 0). */
    val lines: List<String> = emptyList()
)

/**
 * Body for `PUT /api/v1/connectors/{id}/config`.
 * Keys are connector-specific; unknown keys are rejected by the connector.
 */
data class ConnectorConfigureRequest(
    val configuration: Map<String, Any?> = emptyMap()
)

data class ConnectorConfigResponse(
    val id: String,
    val configuration: Map<String, Any?>
)

// ---------------------------------------------------------------------------
// Legacy / typed status shapes (still useful for focused unit tests)
// ---------------------------------------------------------------------------

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
 * @deprecated Prefer [ConnectorInfoResponse] from the connectors control plane.
 */
data class EntraDirectoryConnectorStatusResponse(
    val id: String,
    val enabled: Boolean,
    val configured: Boolean,
    val running: Boolean,
    val autoStart: Boolean,
    val refreshIntervalMs: Long,
    val groupNamePrefix: String,
    val includeTransitiveMembers: Boolean,
    val refreshInProgress: Boolean,
    val lastLoadedAt: Instant?,
    val lastRefreshStartedAt: Instant?,
    val lastRefreshFinishedAt: Instant?,
    val lastRefreshBy: String?,
    val lastStartedBy: String?,
    val lastStartedAt: Instant?,
    val lastStoppedBy: String?,
    val lastStoppedAt: Instant?,
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
