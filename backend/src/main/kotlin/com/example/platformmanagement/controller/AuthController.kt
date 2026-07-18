package com.example.platformmanagement.controller

import com.example.platformmanagement.dto.AuthenticatedUserResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal

/**
 * Exposes the authenticated Microsoft Entra ID principal.
 * Any authenticated caller (any role) may inspect their own token claims.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    /**
     * Returns claims/authorities from the current access token.
     * Requires a valid Entra JWT (`Authorization: Bearer …`).
     */
    @GetMapping("/me")
    @PreAuthorize("@authz.isAuthenticated()")
    fun me(
        @AuthenticationPrincipal jwt: Jwt?,
        principal: Principal?
    ): AuthenticatedUserResponse {
        val token = jwt
            ?: (principal as? JwtAuthenticationToken)?.token
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

        val authorities: List<String> = ((principal as? JwtAuthenticationToken)?.authorities ?: emptyList())
            .mapNotNull { it?.authority }
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
}
