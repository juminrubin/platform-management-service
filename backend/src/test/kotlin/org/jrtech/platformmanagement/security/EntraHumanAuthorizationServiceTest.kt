package org.jrtech.platformmanagement.security

import org.jrtech.platformmanagement.config.AppSecurityProperties
import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.entra.EntraDirectoryMember
import org.jrtech.platformmanagement.entra.EntraGroup
import org.jrtech.platformmanagement.entra.EntraGroupDirectoryService
import org.jrtech.platformmanagement.entra.EntraGroupWithMembers
import org.jrtech.platformmanagement.entra.MicrosoftGraphClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class EntraHumanAuthorizationServiceTest {

    @Test
    fun `human email membership in Platform-System-Maintainer maps to System Maintainer role`() {
        val directory = directoryServiceWithCache(
            mapOf(
                "g-maint" to EntraGroupWithMembers(
                    group = EntraGroup("g-maint", "Platform-System-Maintainer"),
                    members = listOf(
                        EntraDirectoryMember(
                            id = "oid-alice",
                            displayName = "Alice",
                            userPrincipalName = "alice@contoso.com",
                            mail = "alice@contoso.com"
                        )
                    )
                ),
                "g-read" to EntraGroupWithMembers(
                    group = EntraGroup("g-read", "Platform-System-Reader"),
                    members = listOf(
                        EntraDirectoryMember(
                            id = "oid-bob",
                            userPrincipalName = "bob@contoso.com"
                        )
                    )
                )
            )
        )
        val service = EntraHumanAuthorizationService(
            directoryService = directory,
            directoryProperties = EntraDirectoryProperties(enabled = true),
            securityProperties = AppSecurityProperties()
        )

        val jwt = humanJwt(
            preferredUsername = "alice@contoso.com",
            oid = "oid-alice",
            roles = emptyList()
        )

        assertThat(service.resolveMembershipAppRoles(jwt))
            .containsExactly(AppRoles.SYSTEM_MAINTAINER)
        assertThat(service.extractEffectiveRoles(jwt))
            .containsExactly(AppRoles.SYSTEM_MAINTAINER)
        assertThat(service.resolvePlatformGroupNames(jwt))
            .containsExactly("Platform-System-Maintainer")

        val authorities = service.extractAuthorities(jwt).mapNotNull { it.authority }
        assertThat(authorities).contains(
            "ROLE_${AppRoles.SYSTEM_MAINTAINER}",
            AppRoles.SYSTEM_MAINTAINER,
            "GROUP_NAME_Platform-System-Maintainer"
        )
    }

    @Test
    fun `technical JWT roles claim still applies without group membership`() {
        val directory = directoryServiceWithCache(emptyMap())
        val service = EntraHumanAuthorizationService(
            directoryService = directory,
            directoryProperties = EntraDirectoryProperties(enabled = true),
            securityProperties = AppSecurityProperties()
        )
        val jwt = humanJwt(
            preferredUsername = null,
            oid = "mi-oid",
            roles = listOf(AppRoles.CONSUMPTION_REGISTRATOR)
        )
        // No membership roles; JWT roles still in effective set
        assertThat(service.resolveMembershipAppRoles(jwt)).isEmpty()
        assertThat(service.extractEffectiveRoles(jwt))
            .containsExactly(AppRoles.CONSUMPTION_REGISTRATOR)
    }

    @Test
    fun `JWT groups claim resolves via cached group id to Platform-System role`() {
        val directory = directoryServiceWithCache(
            mapOf(
                "group-object-id" to EntraGroupWithMembers(
                    group = EntraGroup("group-object-id", "Platform-System-Reader"),
                    members = emptyList()
                )
            )
        )
        val service = EntraHumanAuthorizationService(
            directoryService = directory,
            directoryProperties = EntraDirectoryProperties(enabled = true),
            securityProperties = AppSecurityProperties()
        )
        val jwt = humanJwt(
            preferredUsername = "carol@contoso.com",
            oid = "oid-carol",
            roles = emptyList(),
            groups = listOf("group-object-id")
        )
        assertThat(service.resolveMembershipAppRoles(jwt))
            .containsExactly(AppRoles.SYSTEM_READER)
    }

    @Test
    fun `Authz canMaintain uses human Platform-System membership`() {
        val directory = directoryServiceWithCache(
            mapOf(
                "g1" to EntraGroupWithMembers(
                    group = EntraGroup("g1", "Platform-System-Maintainer"),
                    members = listOf(
                        EntraDirectoryMember(
                            id = "oid-admin",
                            userPrincipalName = "admin@contoso.com"
                        )
                    )
                )
            )
        )
        val human = EntraHumanAuthorizationService(
            directoryService = directory,
            directoryProperties = EntraDirectoryProperties(enabled = true),
            securityProperties = AppSecurityProperties()
        )
        val provider = mock<ObjectProvider<EntraHumanAuthorizationService>>()
        whenever(provider.getIfAvailable()).thenReturn(human)
        val authz = Authz(AppSecurityProperties(permitAll = false), provider)

        val jwt = humanJwt(
            preferredUsername = "admin@contoso.com",
            oid = "oid-admin",
            roles = emptyList()
        )
        org.springframework.security.core.context.SecurityContextHolder.getContext().authentication =
            org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                jwt,
                listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_access_as_user"))
            )

        assertThat(authz.canMaintain()).isTrue()
        assertThat(authz.canRead()).isTrue()
        org.springframework.security.core.context.SecurityContextHolder.clearContext()
    }

    private fun directoryServiceWithCache(
        groups: Map<String, EntraGroupWithMembers>
    ): EntraGroupDirectoryService {
        val provider = mock<ObjectProvider<MicrosoftGraphClient>>()
        whenever(provider.getIfAvailable()).thenReturn(null)
        val service = EntraGroupDirectoryService(
            properties = EntraDirectoryProperties(enabled = true, autoStart = false),
            graphClientProvider = provider
        )
        service.replaceCacheForTesting(groups)
        return service
    }

    private fun humanJwt(
        preferredUsername: String?,
        oid: String,
        roles: List<String>,
        groups: List<String> = emptyList()
    ): Jwt {
        val now = Instant.parse("2024-06-01T00:00:00Z")
        return Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject(oid)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claims { c ->
                c["oid"] = oid
                if (preferredUsername != null) {
                    c["preferred_username"] = preferredUsername
                    c["email"] = preferredUsername
                }
                if (roles.isNotEmpty()) c["roles"] = roles
                if (groups.isNotEmpty()) c["groups"] = groups
                c["scp"] = "access_as_user"
            }
            .build()
    }
}
