package com.example.platformmanagement.security

import com.example.platformmanagement.config.AppSecurityProperties
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * SpEL-friendly authorization helpers used by `@PreAuthorize("@authz....")`.
 *
 * When `app.security.permit-all=true` (test profile only), role checks pass so
 * unit/persistence tests can run without Entra. Runtime always has permit-all=false.
 *
 * Prefer controller-layer `@PreAuthorize` for HTTP role gates (see README).
 * Keep services free of security annotations so they compose without
 * re-checking roles on internal calls.
 */
@Component("authz")
class Authz(
    private val securityProperties: AppSecurityProperties
) {

    /** True when the app is in open test mode. */
    fun permitAll(): Boolean = securityProperties.permitAll

    /** Any authenticated principal (or permit-all). Used for `/api/v1/auth/me`. */
    fun isAuthenticated(): Boolean {
        if (securityProperties.permitAll) {
            return true
        }
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return false
        return authentication.isAuthenticated &&
            authentication !is AnonymousAuthenticationToken
    }

    /** Full admin: [AppRoles.SYSTEM_MAINTAINER]. */
    fun canMaintain(): Boolean =
        hasAnyRole(AppRoles.SYSTEM_MAINTAINER)

    /**
     * Read any resource list/get endpoint:
     * [AppRoles.SYSTEM_MAINTAINER] or [AppRoles.SYSTEM_READER].
     */
    fun canRead(): Boolean =
        hasAnyRole(AppRoles.SYSTEM_MAINTAINER, AppRoles.SYSTEM_READER)

    /**
     * Entitlement check endpoint:
     * maintainer, system reader, or dedicated [AppRoles.ENTITLEMENT_READER].
     */
    fun canCheckEntitlement(): Boolean =
        hasAnyRole(
            AppRoles.SYSTEM_MAINTAINER,
            AppRoles.SYSTEM_READER,
            AppRoles.ENTITLEMENT_READER
        )

    /**
     * Register consumption:
     * maintainer or [AppRoles.CONSUMPTION_REGISTRATOR].
     */
    fun canRegisterConsumption(): Boolean =
        hasAnyRole(AppRoles.SYSTEM_MAINTAINER, AppRoles.CONSUMPTION_REGISTRATOR)

    /**
     * True if the current principal has any of the given Entra app role values
     * (e.g. `System.Maintainer`), or if permit-all is enabled.
     */
    fun hasAnyRole(vararg roles: String): Boolean {
        if (securityProperties.permitAll) {
            return true
        }
        if (roles.isEmpty()) {
            return false
        }
        val authorities = currentAuthorities()
        return roles.any { role ->
            val value = role.trim()
            if (value.isEmpty()) {
                return@any false
            }
            authorities.contains("ROLE_$value") || authorities.contains(value)
        }
    }

    private fun currentAuthorities(): Set<String> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return emptySet()
        return authentication.authorities.mapNotNull { it?.authority }.toSet()
    }
}
