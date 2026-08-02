package org.jrtech.platformmanagement.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Loads Microsoft Entra security groups (and their members) whose display names
 * start with [groupNamePrefix] (default `Platform-System-`) via Microsoft Graph.
 *
 * Requires **application** permissions (admin consent) on the Graph credential principal:
 * - `Group.Read.All` + `GroupMember.Read.All` + **`User.Read.All`**
 * - or `Directory.Read.All` (covers groups, members, and user profile attributes)
 *
 * User profile fields on members require `User.Read.All` (or Directory.Read.All).
 * The client also casts members to `microsoft.graph.user` and may hydrate via `GET /users/{id}`.
 *
 * Auth (in order of preference when [enabled] is true):
 * 1. Client secret: [clientId] + [clientSecret] + [tenantId]
 * 2. Otherwise [DefaultAzureCredential] (managed identity, `az login`, …)
 *
 * Lifecycle (connector `entra-directory`):
 * - [autoStart] arms the connector on application ready (initial Graph load + schedule)
 * - Periodic refresh runs only while the connector is **running** (start/stop API)
 * - Stop does not cancel an in-flight load; it only prevents the next run
 */
@ConfigurationProperties(prefix = "app.entra-directory")
data class EntraDirectoryProperties(
    /**
     * When false, directory loading is skipped and list APIs return empty snapshots.
     * Keep false in unit tests; enable in environments with Graph credentials.
     */
    val enabled: Boolean = false,

    /** Display-name prefix filter for security groups (case-sensitive Graph `startswith`). */
    val groupNamePrefix: String = "Platform-System-",

    /**
     * When true, load transitive members (nested groups expanded).
     * When false, only direct group members.
     */
    val includeTransitiveMembers: Boolean = false,

    /**
     * How often to rebuild the thread-safe group→members map from Microsoft Graph (ms)
     * while the connector is **running**. Default **900_000** (15 minutes).
     * 0 disables the periodic schedule (start still performs one immediate load).
     */
    val refreshIntervalMs: Long = 900_000L,

    /**
     * When true and [enabled], start the connector on application ready
     * (immediate Graph load for human authorization + arm periodic refresh).
     * Primary ongoing control remains Maintainer Web API start/stop.
     */
    val autoStart: Boolean = true,

    val graphBaseUrl: String = "https://graph.microsoft.com/v1.0",

    /** Entra tenant for client-credentials Graph tokens. Defaults from APP_AZURE_TENANT_ID. */
    val tenantId: String = "",

    /**
     * App registration / managed identity client id used to call Graph.
     * Defaults from APP_AZURE_GRAPH_CLIENT_ID or APP_AZURE_API_CLIENT_ID.
     */
    val clientId: String = "",

    /**
     * Optional client secret for confidential client credentials.
     * Leave empty to use DefaultAzureCredential (MI / developer login).
     */
    val clientSecret: String = "",
)
