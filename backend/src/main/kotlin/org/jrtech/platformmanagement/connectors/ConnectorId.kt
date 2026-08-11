package org.jrtech.platformmanagement.connectors

import org.jrtech.platformmanagement.exception.ResourceNotFoundException

/**
 * Stable identifiers for integrations exposed as connectors.
 *
 * [pathId] is the external API segment used in `/api/v1/connectors/{id}`.
 */
enum class ConnectorId(val pathId: String) {
    ENTRA_DIRECTORY("entra-directory"),
    CONSUMPTION_BLOB_AVRO("consumption-storage"),
    CONSUMPTION_EVENT_HUB("consumption-eventhub"),
    /** Reloads the entitlement check cache from the durable catalog store. */
    DATASOURCE_LOADING("datasource-loading");

    companion object {
        /**
         * Resolves a path segment or enum name (case-insensitive).
         * Accepts both `consumption-storage` and `CONSUMPTION_BLOB_AVRO`.
         */
        fun fromPathId(raw: String): ConnectorId? {
            val key = raw.trim()
            if (key.isEmpty()) return null
            entries.find { it.pathId.equals(key, ignoreCase = true) }?.let { return it }
            return entries.find { it.name.equals(key, ignoreCase = true) }
        }

        fun requirePathId(raw: String): ConnectorId =
            fromPathId(raw)
                ?: throw ResourceNotFoundException(
                    "Unknown connector id '$raw'. Known: ${entries.joinToString { it.pathId }}"
                )
    }
}
