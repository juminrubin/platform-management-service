package com.example.platformmanagement.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Verifies Microsoft Entra-style JWT resource-server authorization on the API.
 *
 * Uses Spring Security's mock JWT support (no live call to login.microsoftonline.com).
 * Profile: test + secure-like properties (permit-all=false).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig::class)
@TestPropertySource(
    properties = [
        "app.security.permit-all=false",
        "app.security.required-scope="
    ]
)
class MicrosoftEntraSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `API rejects unauthenticated requests with 401`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")) }
        }
    }

    @Test
    fun `API allows System Maintainer JWT on admin endpoints`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("user-object-id")
                        token.claim("preferred_username", "admin@contoso.com")
                        token.claim("tid", "tenant-guid")
                        token.claim("roles", listOf(AppRoles.SYSTEM_MAINTAINER))
                        token.claim("azp", "client-app-id")
                    }
                    .authorities(SimpleGrantedAuthority("ROLE_${AppRoles.SYSTEM_MAINTAINER}"))
            )
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `auth me returns Entra claims from JWT`() {
        mockMvc.get("/api/v1/auth/me") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("user-object-id")
                        token.claim("preferred_username", "alice@contoso.com")
                        token.claim("name", "Alice Contoso")
                        token.claim("tid", "tenant-guid")
                        token.claim("roles", listOf(AppRoles.ENTITLEMENT_READER))
                        token.claim("azp", "client-app-id")
                        token.audience(listOf("api-client-id"))
                    }
                    .authorities(SimpleGrantedAuthority("ROLE_${AppRoles.ENTITLEMENT_READER}"))
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.subject") { value("user-object-id") }
            jsonPath("$.preferredUsername") { value("alice@contoso.com") }
            jsonPath("$.name") { value("Alice Contoso") }
            jsonPath("$.tenantId") { value("tenant-guid") }
            jsonPath("$.clientId") { value("client-app-id") }
            jsonPath("$.roles[0]") { value(AppRoles.ENTITLEMENT_READER) }
        }
    }

    @Test
    fun `health remains public without JWT`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `h2 console is not denied from localhost without JWT`() {
        val status = mockMvc.get("/h2-console/") {
            with { request ->
                request.remoteAddr = "127.0.0.1"
                request
            }
        }.andReturn().response.status

        // 200/302/404 are fine depending on console registration; never 401/403 from loopback
        assertThat(status).isNotIn(401, 403)
    }

    @Test
    fun `h2 console is denied from non-localhost`() {
        val status = mockMvc.get("/h2-console/") {
            with { request ->
                request.remoteAddr = "203.0.113.50"
                request
            }
        }.andReturn().response.status

        // Anonymous denied → 401 (entry point); authenticated denied → 403. Either means blocked.
        assertThat(status).isIn(401, 403)
    }
}
