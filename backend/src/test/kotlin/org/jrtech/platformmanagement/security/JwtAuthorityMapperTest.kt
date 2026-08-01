package org.jrtech.platformmanagement.security

import org.jrtech.platformmanagement.config.AppSecurityProperties
import org.jrtech.platformmanagement.config.ApplicationRegistrationSecurity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class JwtAuthorityMapperTest {

    @Test
    fun `maps roles claim list to ROLE_ authorities`() {
        val jwt = jwtWithClaims(
            mapOf(
                "sub" to "user-1",
                "roles" to listOf("System.Reader", "System.Maintainer")
            )
        )
        val authorities = JwtAuthorityMapper.extractAuthorities(jwt).mapNotNull { it.authority }
        assertThat(authorities).contains(
            "ROLE_System.Reader",
            "System.Reader",
            "ROLE_System.Maintainer",
            "System.Maintainer"
        )
    }

    @Test
    fun `maps scp short name and full URI to SCOPE_ authorities`() {
        val jwt = jwtWithClaims(
            mapOf(
                "sub" to "user-1",
                "scp" to "api://11111111-1111-1111-1111-111111111111/access_as_user"
            )
        )
        val authorities = JwtAuthorityMapper.extractAuthorities(jwt).mapNotNull { it.authority }
        assertThat(authorities).contains(
            "SCOPE_api://11111111-1111-1111-1111-111111111111/access_as_user",
            "SCOPE_access_as_user"
        )
        assertThat(
            JwtAuthorityMapper.matchesRequiredScope(authorities, "access_as_user")
        ).isTrue()
        assertThat(
            JwtAuthorityMapper.matchesRequiredScope(
                authorities,
                "api://11111111-1111-1111-1111-111111111111/access_as_user"
            )
        ).isTrue()
    }

    @Test
    fun `roles as single string still maps`() {
        val jwt = jwtWithClaims(mapOf("sub" to "u", "roles" to "System.Reader"))
        val authorities = JwtAuthorityMapper.extractAuthorities(jwt).mapNotNull { it.authority }
        assertThat(authorities).contains("ROLE_System.Reader", "System.Reader")
    }

    @Test
    fun `maps groups claim to GROUP_ authorities`() {
        val groupId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val jwt = jwtWithClaims(
            mapOf(
                "sub" to "user-1",
                "groups" to listOf(groupId)
            )
        )
        val authorities = JwtAuthorityMapper.extractAuthorities(jwt).mapNotNull { it.authority }
        assertThat(authorities).contains("GROUP_$groupId")
        assertThat(JwtAuthorityMapper.extractGroups(jwt)).containsExactly(groupId)
    }

    @Test
    fun `global group-role mapping synthesizes app roles from groups claim`() {
        val groupId = "11111111-2222-3333-4444-555555555555"
        val props = AppSecurityProperties(
            groupRoleMappings = mapOf(groupId to listOf(AppRoles.SYSTEM_MAINTAINER))
        )
        val jwt = jwtWithClaims(
            mapOf(
                "sub" to "user-1",
                "groups" to listOf(groupId)
            )
        )
        val authorities = JwtAuthorityMapper.extractAuthorities(jwt, props).mapNotNull { it.authority }
        assertThat(authorities).contains(
            "GROUP_$groupId",
            "ROLE_${AppRoles.SYSTEM_MAINTAINER}",
            AppRoles.SYSTEM_MAINTAINER
        )
        assertThat(JwtAuthorityMapper.extractEffectiveRoles(jwt, props))
            .containsExactly(AppRoles.SYSTEM_MAINTAINER)
    }

    @Test
    fun `application registration group and scope mapping matches azp client id`() {
        val appRegId = "api-app-client-id"
        val groupId = "gggggggg-1111-2222-3333-444444444444"
        val props = AppSecurityProperties(
            applicationRegistrations = listOf(
                ApplicationRegistrationSecurity(
                    clientId = appRegId,
                    oauthScopes = listOf("access_as_user", "api://$appRegId/access_as_user"),
                    groupRoleMappings = mapOf(groupId to listOf(AppRoles.SYSTEM_READER))
                )
            )
        )
        val jwt = jwtWithClaims(
            mapOf(
                "sub" to "user-1",
                "azp" to appRegId,
                "aud" to listOf(appRegId),
                "groups" to listOf(groupId),
                "scp" to "access_as_user"
            )
        )

        val authorities = JwtAuthorityMapper.extractAuthorities(jwt, props).mapNotNull { it.authority }
        assertThat(authorities).contains(
            "SCOPE_access_as_user",
            "GROUP_$groupId",
            "ROLE_${AppRoles.SYSTEM_READER}"
        )
        assertThat(JwtAuthorityMapper.matchingApplicationRegistrations(jwt, props))
            .extracting<String> { it.clientId }
            .containsExactly(appRegId)
        assertThat(JwtAuthorityMapper.expectedOauthScopes(jwt, props))
            .containsExactlyInAnyOrder("access_as_user", "api://$appRegId/access_as_user")
        assertThat(JwtAuthorityMapper.extractApplicationRegistrationIds(jwt))
            .contains(appRegId)
    }

    @Test
    fun `application registration mapping matches api audience form`() {
        val appRegId = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff"
        val groupId = "99999999-8888-7777-6666-555555555555"
        val props = AppSecurityProperties(
            applicationRegistrations = listOf(
                ApplicationRegistrationSecurity(
                    clientId = appRegId,
                    groupRoleMappings = mapOf(groupId to listOf(AppRoles.ENTITLEMENT_READER))
                )
            )
        )
        val jwt = jwtWithClaims(
            mapOf(
                "sub" to "user-1",
                "aud" to listOf("api://$appRegId"),
                "groups" to listOf(groupId)
            )
        )
        assertThat(JwtAuthorityMapper.extractEffectiveRoles(jwt, props))
            .containsExactly(AppRoles.ENTITLEMENT_READER)
    }

    @Test
    fun `unrelated application registration does not apply group mappings`() {
        val props = AppSecurityProperties(
            applicationRegistrations = listOf(
                ApplicationRegistrationSecurity(
                    clientId = "other-app-id",
                    groupRoleMappings = mapOf(
                        "group-1" to listOf(AppRoles.SYSTEM_MAINTAINER)
                    )
                )
            )
        )
        val jwt = jwtWithClaims(
            mapOf(
                "sub" to "user-1",
                "azp" to "this-app-id",
                "groups" to listOf("group-1")
            )
        )
        assertThat(JwtAuthorityMapper.extractEffectiveRoles(jwt, props)).isEmpty()
        val authorities = JwtAuthorityMapper.extractAuthorities(jwt, props).mapNotNull { it.authority }
        assertThat(authorities).contains("GROUP_group-1")
        assertThat(authorities).doesNotContain("ROLE_${AppRoles.SYSTEM_MAINTAINER}")
    }

    @Test
    fun `extractScopes accepts list-shaped scope claim and multi-value scp`() {
        val listJwt = jwtWithClaims(
            mapOf("sub" to "u", "scope" to listOf("openid", "access_as_user"))
        )
        assertThat(JwtAuthorityMapper.extractScopes(listJwt))
            .containsExactly("openid", "access_as_user")

        val multiJwt = jwtWithClaims(
            mapOf("sub" to "u", "scp" to "access_as_user openid profile")
        )
        assertThat(JwtAuthorityMapper.extractScopes(multiJwt))
            .containsExactly("access_as_user", "openid", "profile")
    }

    @Test
    fun `JWT roles plus group-mapped roles are merged in extractEffectiveRoles`() {
        val groupId = "group-merge"
        val props = AppSecurityProperties(
            groupRoleMappings = mapOf(groupId to listOf(AppRoles.SYSTEM_READER))
        )
        val jwt = jwtWithClaims(
            mapOf(
                "sub" to "u",
                "roles" to listOf(AppRoles.ENTITLEMENT_READER),
                "groups" to listOf(groupId)
            )
        )
        assertThat(JwtAuthorityMapper.extractEffectiveRoles(jwt, props))
            .containsExactlyInAnyOrder(AppRoles.ENTITLEMENT_READER, AppRoles.SYSTEM_READER)
    }

    private fun jwtWithClaims(claims: Map<String, Any>): Jwt {
        val now = Instant.parse("2024-06-01T00:00:00Z")
        return Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claims { c -> c.putAll(claims) }
            .build()
    }
}
