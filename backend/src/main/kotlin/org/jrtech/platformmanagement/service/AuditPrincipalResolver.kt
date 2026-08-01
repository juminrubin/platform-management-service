package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.AuditActors
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Resolves a stable audit principal string from the current security context.
 * Used for connector start/stop and future backfill job [requested_by].
 */
@Component
class AuditPrincipalResolver {

    fun current(): String {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return AuditActors.SYSTEM
        val jwt: Jwt? = when (val principal = authentication.principal) {
            is Jwt -> principal
            else -> (authentication as? JwtAuthenticationToken)?.token
        }
        if (jwt != null) {
            sequenceOf("preferred_username", "upn", "email", "oid")
                .mapNotNull { claim -> jwt.getClaimAsString(claim)?.trim()?.takeIf { it.isNotEmpty() } }
                .firstOrNull()
                ?.let { return it }
            jwt.subject?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val name = authentication.name?.trim().orEmpty()
        if (name.isNotEmpty() && name != "anonymousUser") {
            return name
        }
        return AuditActors.SYSTEM
    }
}
