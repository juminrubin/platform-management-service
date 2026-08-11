package org.jrtech.platformmanagement.cache

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * In-process entitlement check index (`app.entitlement-check-cache`).
 *
 * Used by [org.jrtech.platformmanagement.service.EntitlementService.checkByCallerAndService]
 * for low-latency caller / service / entitlement lookups.
 */
@ConfigurationProperties(prefix = "app.entitlement-check-cache")
data class EntitlementCheckCacheProperties(
    /**
     * When true, entitlement checks read from the in-memory index after it has loaded.
     * When false, checks always hit the database.
     */
    val enabled: Boolean = true,

    /**
     * Load the index once the application is ready (after seed / Flyway).
     */
    val loadOnStartup: Boolean = true,

    /**
     * When true, a fixed-delay scheduled task reloads the index periodically.
     */
    val scheduledRefreshEnabled: Boolean = true,

    /**
     * Delay between the end of one scheduled reload and the start of the next (ms).
     * Default 3_600_000 = 1 hour.
     */
    val refreshIntervalMs: Long = 3_600_000L
)
