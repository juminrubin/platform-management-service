package org.jrtech.platformmanagement.connectors.runtime

import org.jrtech.platformmanagement.connectors.ConnectorHealthContributor
import org.jrtech.platformmanagement.dto.ConnectorInfoResponse

/**
 * Backend process facade for an integration connector.
 *
 * Control plane (this type): info, configuration, start, stop.
 * Data plane: domain-specific REST groups (e.g. /api/v1/entra/groups,
 * /api/v1/consumption/blob) — never mixed into connector control routes.
 */
interface ManagedConnector : ConnectorHealthContributor {

    /** Full operator view: runtime state, public config, log snapshot (max 32 KB). */
    fun info(): ConnectorInfoResponse

    /** Public (non-secret) configuration view. */
    fun configuration(): Map<String, Any?>

    /**
     * Apply runtime configuration updates (connector-specific keys).
     * @return resulting public configuration after apply
     */
    fun configure(updates: Map<String, Any?>): Map<String, Any?>

    /** Start / arm the connector process. */
    fun start(actor: String): ConnectorInfoResponse

    /**
     * Stop / disarm the connector process.
     * In-flight work is not hard-cancelled unless the connector documents otherwise.
     */
    fun stop(actor: String): ConnectorInfoResponse
}
