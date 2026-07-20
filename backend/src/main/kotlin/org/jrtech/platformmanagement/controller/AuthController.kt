package org.jrtech.platformmanagement.controller

import org.jrtech.platformmanagement.dto.AuthenticatedUserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Exposes the authenticated Microsoft Entra ID principal.
 * Any caller with a valid Entra access token may inspect their own claims
 * (no app-role requirement).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
@SecurityRequirement(name = "bearer-jwt")
class AuthController {

    /**
     * Returns claims/authorities from the current access token.
     * Requires a valid Entra JWT only — open to all authenticated users/roles.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Current authenticated principal",
        description = "Returns Entra JWT claims and mapped authorities. " +
            "Any authenticated user (valid Bearer token) may call this — no app role required."
    )
    fun me(
        @AuthenticationPrincipal jwt: Jwt?,
        authentication: Authentication?
    ): AuthenticatedUserResponse {
        val auth = authentication ?: SecurityContextHolder.getContext().authentication
        val token = resolveJwt(jwt, auth)
            ?: throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "No JWT principal — call with Authorization: Bearer <entra-access-token>"
            )

        val scopes = (token.getClaimAsString("scp") ?: token.getClaimAsString("scope") ?: "")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val roles = (token.getClaimAsStringList("roles") ?: emptyList())
            .filterNotNull()

        val authorities: List<String> = (auth?.authorities ?: emptyList())
            .mapNotNull { it.authority }
            .sorted()

        val audience: List<String> = token.audience?.filterNotNull() ?: emptyList()

        return AuthenticatedUserResponse(
            subject = token.subject ?: token.id ?: "unknown",
            preferredUsername = token.getClaimAsString("preferred_username"),
            name = token.getClaimAsString("name"),
            clientId = token.getClaimAsString("azp")
                ?: token.getClaimAsString("appid"),
            tenantId = token.getClaimAsString("tid"),
            audience = audience,
            authorities = authorities,
            scopes = scopes,
            roles = roles
        )
    }

    private fun resolveJwt(principalJwt: Jwt?, authentication: Authentication?): Jwt? {
        if (principalJwt != null) {
            return principalJwt
        }
        if (authentication is JwtAuthenticationToken) {
            return authentication.token
        }
        val principal = authentication?.principal
        if (principal is Jwt) {
            return principal
        }
        return null
    }
}
