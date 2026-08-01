package org.jrtech.platformmanagement.connectors.entra

import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.connectors.ConnectorHealthContributor
import org.jrtech.platformmanagement.connectors.ConnectorHealthView
import org.jrtech.platformmanagement.connectors.ConnectorId
import org.jrtech.platformmanagement.dto.EntraDirectoryConnectorStatusResponse
import org.jrtech.platformmanagement.entra.EntraGroupDirectoryService
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.logging.logger
import org.springframework.stereotype.Service

/**
 * Connector facade for Entra Platform-System-* group/member Graph loading.
 *
 * - Monitor: [status] / [health] for Maintainer control plane
 * - Run: [start] triggers an immediate Graph refresh
 * - View data: still via `/api/v1/entra/` endpoints (unchanged)
 */
@Service
class EntraDirectoryConnector(
    private val properties: EntraDirectoryProperties,
    private val directoryService: EntraGroupDirectoryService
) : ConnectorHealthContributor {

    private val log = logger()

    override val id: ConnectorId = ConnectorId.ENTRA_DIRECTORY

    override fun isEnabled(): Boolean = properties.enabled

    fun status(): EntraDirectoryConnectorStatusResponse {
        val snap = directoryService.snapshot()
        val configured = directoryService.hasGraphClient()
        val inProgress = directoryService.isRefreshInProgress()
        val detail = when {
            !properties.enabled -> "disabled"
            inProgress -> "refresh-in-progress"
            directoryService.lastError() != null -> "last-error"
            snap.loadedAt == null -> "not-loaded"
            else -> "ready"
        }
        return EntraDirectoryConnectorStatusResponse(
            id = id.pathId,
            enabled = properties.enabled,
            configured = configured,
            loadOnStartup = properties.loadOnStartup,
            refreshIntervalMs = properties.refreshIntervalMs,
            groupNamePrefix = properties.groupNamePrefix,
            includeTransitiveMembers = properties.includeTransitiveMembers,
            refreshInProgress = inProgress,
            lastLoadedAt = directoryService.lastLoadedAt(),
            lastRefreshStartedAt = directoryService.lastRefreshStartedAt(),
            lastRefreshFinishedAt = directoryService.lastRefreshFinishedAt(),
            lastRefreshBy = directoryService.lastRefreshBy(),
            lastError = directoryService.lastError(),
            groupCount = snap.groupCount,
            memberCount = snap.memberCount,
            uniqueMemberCount = directoryService.allMembers().size,
            detail = detail
        )
    }

    /** Triggers an immediate Graph reload (same as POST /api/v1/entra/groups/refresh). */
    fun start(actor: String): EntraDirectoryConnectorStatusResponse {
        if (!properties.enabled) {
            throw BadRequestException(
                "Entra directory connector is disabled " +
                    "(set app.entra-directory.enabled=true)"
            )
        }
        try {
            directoryService.refresh(triggeredBy = actor)
            log.info("Entra directory connector refresh started/completed by={}", actor)
        } catch (ex: Exception) {
            throw if (ex is BadRequestException) ex
            else BadRequestException(
                "Failed to refresh Entra directory: ${ex.message ?: ex.javaClass.simpleName}"
            )
        }
        return status()
    }

    override fun health(): ConnectorHealthView {
        val s = status()
        val statusLabel = when {
            !s.enabled -> "DISABLED"
            s.refreshInProgress -> "RUNNING"
            s.lastError != null -> "DEGRADED"
            !s.configured -> "DOWN"
            s.lastLoadedAt == null -> "STOPPED"
            else -> "UP"
        }
        return ConnectorHealthView(
            id = id,
            enabled = s.enabled,
            status = statusLabel,
            detail = s.detail,
            attributes = buildMap {
                put("configured", s.configured.toString())
                put("groupCount", s.groupCount.toString())
                put("memberCount", s.memberCount.toString())
                put("uniqueMemberCount", s.uniqueMemberCount.toString())
                put("refreshIntervalMs", s.refreshIntervalMs.toString())
                put("groupNamePrefix", s.groupNamePrefix)
                s.lastLoadedAt?.let { put("lastLoadedAt", it.toString()) }
                s.lastRefreshBy?.let { put("lastRefreshBy", it) }
                s.lastError?.let { put("lastError", it) }
            }
        )
    }
}
