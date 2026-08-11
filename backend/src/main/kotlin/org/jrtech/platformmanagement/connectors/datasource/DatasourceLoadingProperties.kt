package org.jrtech.platformmanagement.connectors.datasource

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Datasource-loading connector: rebuilds the entitlement check cache from the durable store.
 *
 * Catalog seed from JSON is **not** performed by the app; use external scripts to populate
 * Azure Table / the store.
 */
@ConfigurationProperties(prefix = "app.connectors.datasource-loading")
data class DatasourceLoadingProperties(
    /** Master switch for the connector process. */
    val enabled: Boolean = true,
    /** Start on ApplicationReady (cache load + schedule). */
    val autoStart: Boolean = true,
    /** Fixed delay between cache reloads (ms). Default 1 hour. */
    val refreshIntervalMs: Long = 3_600_000L
)
