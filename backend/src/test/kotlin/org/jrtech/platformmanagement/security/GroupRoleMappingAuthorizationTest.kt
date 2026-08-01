package org.jrtech.platformmanagement.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Verifies Entra security group → app role mapping (global and per Application Registration).
 *
 * Tokens may carry `groups` without a `roles` claim; configured mappings synthesize
 * the same app roles used by `@PreAuthorize` / [Authz].
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig::class)
@TestPropertySource(
    properties = [
        "app.security.permit-all=false",
        "app.security.required-scope=",
        "app.security.group-role-mappings.reader-group-oid[0]=System.Reader",
        "app.security.application-registrations[0].client-id=spa-client-id",
        "app.security.application-registrations[0].oauth-scopes[0]=access_as_user",
        "app.security.application-registrations[0].group-role-mappings.maintainer-group-oid[0]=System.Maintainer"
    ]
)
class GroupRoleMappingAuthorizationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `global group mapping grants System Reader without roles claim`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("group-reader-user")
                        token.claim("groups", listOf("reader-group-oid"))
                        token.claim("scp", "access_as_user")
                        token.claim("tid", "tenant-guid")
                    }
                    // Only scope authority — roles come from group mapping fallback in Authz
                    .authorities(SimpleGrantedAuthority("SCOPE_access_as_user"))
            )
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `application registration group mapping grants Maintainer when azp matches`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("spa-admin")
                        token.claim("azp", "spa-client-id")
                        token.claim("groups", listOf("maintainer-group-oid"))
                        token.claim("scp", "access_as_user")
                        token.claim("tid", "tenant-guid")
                    }
                    .authorities(
                        SimpleGrantedAuthority("SCOPE_access_as_user"),
                        SimpleGrantedAuthority("GROUP_maintainer-group-oid")
                    )
            )
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `auth me surfaces groups scopes and matched application registration`() {
        mockMvc.get("/api/v1/auth/me") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("me-user")
                        token.claim("azp", "spa-client-id")
                        token.claim("groups", listOf("maintainer-group-oid"))
                        token.claim("scp", "access_as_user")
                        token.claim("tid", "tenant-guid")
                    }
                    .authorities(
                        SimpleGrantedAuthority("SCOPE_access_as_user"),
                        SimpleGrantedAuthority("GROUP_maintainer-group-oid"),
                        SimpleGrantedAuthority("ROLE_${AppRoles.SYSTEM_MAINTAINER}")
                    )
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.groups[0]") { value("maintainer-group-oid") }
            jsonPath("$.scopes[0]") { value("access_as_user") }
            jsonPath("$.roles[0]") { value(AppRoles.SYSTEM_MAINTAINER) }
            jsonPath("$.matchedApplicationRegistrationIds[0]") { value("spa-client-id") }
            jsonPath("$.expectedScopes[0]") { value("access_as_user") }
        }
    }

    @Test
    fun `unknown group without roles remains forbidden on protected endpoints`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("unknown-group-user")
                        token.claim("groups", listOf("unmapped-group-oid"))
                        token.claim("azp", "spa-client-id")
                    }
                    .authorities(SimpleGrantedAuthority("GROUP_unmapped-group-oid"))
            )
        }.andExpect {
            status { isForbidden() }
        }
    }
}
