package org.jrtech.platformmanagement.connectors

/**
 * Operator-facing health snapshot for a connector.
 */
data class ConnectorHealthView(
    val id: ConnectorId,
    val enabled: Boolean,
    /** UP | DOWN | DEGRADED | DISABLED | STOPPED | RUNNING */
    val status: String,
    val detail: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Optional health contribution for admin listing.
 */
interface ConnectorHealthContributor {
    val id: ConnectorId
    fun isEnabled(): Boolean
    fun health(): ConnectorHealthView
}
