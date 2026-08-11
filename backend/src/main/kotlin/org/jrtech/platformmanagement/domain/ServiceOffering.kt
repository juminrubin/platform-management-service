package org.jrtech.platformmanagement.domain

import java.time.Instant

/** Catalog entry for a service that can be entitled. */
data class ServiceOffering(
    val id: String,
    var name: String,
    var category: String,
    var createdBy: String,
    var updatedBy: String,
    var description: String? = null,
    var provider: String = DEFAULT_PROVIDER,
    var config: String = "{}",
    var active: Boolean = true,
    var createdAt: Instant = UtcTimestamps.now(),
    var updatedAt: Instant = UtcTimestamps.now()
) {
    companion object {
        const val DEFAULT_PROVIDER: String = "SYSTEM"
    }
}
