package org.jrtech.platformmanagement.persistence

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Azure Table Storage settings (`app.azure-table`).
 *
 * When [enabled] is false, the app uses the process-local [InMemoryPlatformStore]
 * (local/dev/tests). When true, repositories talk to Azure Table Storage using
 * [app.azure.credential] (UAMI / SP / SAMI) + [endpoint], or optional [connectionString].
 */
@ConfigurationProperties(prefix = "app.azure-table")
data class AzureTableProperties(
    /** Master switch: false → in-memory store; true → Azure Table. */
    val enabled: Boolean = false,
    /**
     * Table service endpoint, e.g. `https://mystorage.table.core.windows.net`.
     * Required when [enabled] and [connectionString] is blank.
     */
    val endpoint: String = "",
    /**
     * Optional connection string (local Azurite / account key). Prefer MI + [endpoint] in prod.
     */
    val connectionString: String = "",
    val tablePrefix: String = "pms",
    val servicesTable: String = "services",
    val participantsTable: String = "participants",
    val callersTable: String = "callers",
    val entitlementsTable: String = "entitlements",
    val consumptionsTable: String = "consumptions",
    /** Secondary index table: sourceRefId → consumption id. */
    val consumptionSourceRefTable: String = "consumptionsourceref",
    /** Create tables on startup if missing. */
    val createTablesIfNotExist: Boolean = true
) {
    fun tableName(suffix: String): String {
        val prefix = tablePrefix.trim().trimEnd()
        val name = suffix.trim()
        return if (prefix.isEmpty()) name else "$prefix$name"
    }

    fun isConfigured(): Boolean =
        connectionString.trim().isNotEmpty() || endpoint.trim().isNotEmpty()
}
