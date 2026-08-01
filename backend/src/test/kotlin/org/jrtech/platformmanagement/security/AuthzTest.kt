package org.jrtech.platformmanagement.security

import org.jrtech.platformmanagement.config.AppSecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class AuthzTest {

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `permit-all short-circuits role and auth checks`() {
        val authz = authz(AppSecurityProperties(permitAll = true, requiredScope = ""))
        assertThat(authz.permitAll()).isTrue()
        assertThat(authz.isAuthenticated()).isTrue()
        assertThat(authz.canMaintain()).isTrue()
        assertThat(authz.canRead()).isTrue()
        assertThat(authz.canCheckEntitlement()).isTrue()
        assertThat(authz.canRegisterConsumption()).isTrue()
        assertThat(authz.hasAnyRole(AppRoles.SYSTEM_MAINTAINER)).isTrue()
    }

    @Test
    fun `isAuthenticated rejects missing and anonymous principals`() {
        val authz = authz(AppSecurityProperties(permitAll = false, requiredScope = ""))
        SecurityContextHolder.clearContext()
        assertThat(authz.isAuthenticated()).isFalse()

        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        assertThat(authz.isAuthenticated()).isFalse()
    }

    @Test
    fun `role helpers honor ROLE_ prefix and bare role values`() {
        val authz = authz(AppSecurityProperties(permitAll = false, requiredScope = ""))
        setAuth(SimpleGrantedAuthority("ROLE_${AppRoles.SYSTEM_READER}"))
        assertThat(authz.isAuthenticated()).isTrue()
        assertThat(authz.canRead()).isTrue()
        assertThat(authz.canMaintain()).isFalse()
        assertThat(authz.canCheckEntitlement()).isTrue()
        assertThat(authz.canRegisterConsumption()).isFalse()

        setAuth(SimpleGrantedAuthority(AppRoles.CONSUMPTION_REGISTRATOR))
        assertThat(authz.canRegisterConsumption()).isTrue()
        assertThat(authz.canRead()).isFalse()
    }

    @Test
    fun `hasAnyRole handles empty roles and blank role names`() {
        val authz = authz(AppSecurityProperties(permitAll = false, requiredScope = ""))
        setAuth(SimpleGrantedAuthority("ROLE_${AppRoles.SYSTEM_MAINTAINER}"))
        assertThat(authz.hasAnyRole()).isFalse()
        assertThat(authz.hasAnyRole("  ", AppRoles.SYSTEM_MAINTAINER)).isTrue()
        assertThat(authz.hasAnyRole("  ")).isFalse()
    }

    @Test
    fun `hasAnyRole returns false when security context has no authentication`() {
        val authz = authz(AppSecurityProperties(permitAll = false, requiredScope = ""))
        SecurityContextHolder.clearContext()
        assertThat(authz.hasAnyRole(AppRoles.SYSTEM_MAINTAINER)).isFalse()
    }

    @Test
    fun `canRead falls back to JWT roles claim when GrantedAuthorities omit ROLE_ prefix mapping`() {
        val authz = authz(AppSecurityProperties(permitAll = false, requiredScope = ""))
        val now = Instant.parse("2024-06-01T00:00:00Z")
        val jwt = Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("user-1")
            .claim("roles", listOf("System.Reader"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build()
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("SCOPE_access_as_user")))
        assertThat(authz.canRead()).isTrue()
        assertThat(authz.canMaintain()).isFalse()
    }

    @Test
    fun `canRead falls back to group-role mapping when token has groups but no roles claim`() {
        val groupId = "maintainer-group-oid"
        val authz = authz(
            AppSecurityProperties(
                permitAll = false,
                requiredScope = "",
                groupRoleMappings = mapOf(groupId to listOf(AppRoles.SYSTEM_READER))
            )
        )
        val now = Instant.parse("2024-06-01T00:00:00Z")
        val jwt = Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("user-1")
            .claim("groups", listOf(groupId))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build()
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("SCOPE_access_as_user")))
        assertThat(authz.canRead()).isTrue()
        assertThat(authz.canMaintain()).isFalse()
    }

    private fun authz(
        props: AppSecurityProperties,
        human: EntraHumanAuthorizationService? = null
    ): Authz {
        val provider = mock<ObjectProvider<EntraHumanAuthorizationService>>()
        whenever(provider.getIfAvailable()).thenReturn(human)
        return Authz(props, provider)
    }

    private fun setAuth(vararg authorities: SimpleGrantedAuthority) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user", "n/a", authorities.toList())
    }
}
