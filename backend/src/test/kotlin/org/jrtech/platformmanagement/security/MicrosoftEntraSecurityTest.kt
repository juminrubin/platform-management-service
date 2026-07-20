package org.jrtech.platformmanagement.security

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
    fun `auth me is open to any authenticated JWT without app roles`() {
        mockMvc.get("/api/v1/auth/me") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("user-no-roles")
                        token.claim("preferred_username", "noroles@contoso.com")
                        token.claim("tid", "tenant-guid")
                        token.claim("azp", "spa-client-id")
                    }
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.subject") { value("user-no-roles") }
            jsonPath("$.preferredUsername") { value("noroles@contoso.com") }
        }
    }

    @Test
    fun `health remains public without JWT`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `OpenAPI docs and Swagger UI are public without JWT`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.openapi") { exists() }
            jsonPath("$.paths./api/v1/participants") { exists() }
            jsonPath("$.components.securitySchemes.bearer-jwt") { exists() }
            // Bearer scheme is advertised for Try it out, not required to open the UI
            jsonPath("$.security[0].bearer-jwt") { exists() }
        }

        mockMvc.get("/swagger-ui/index.html").andExpect {
            status { isOk() }
        }

        mockMvc.get("/swagger-ui.html").andExpect {
            // springdoc welcome path redirects into /swagger-ui/index.html
            status { is3xxRedirection() }
        }

        // Bare directory URL is redirected to the SPA index (SwaggerUiRedirectConfig)
        mockMvc.get("/swagger-ui/").andExpect {
            status { is3xxRedirection() }
            header { string("Location", org.hamcrest.Matchers.containsString("/swagger-ui/index.html")) }
        }
        mockMvc.get("/swagger-ui").andExpect {
            status { is3xxRedirection() }
            header { string("Location", org.hamcrest.Matchers.containsString("/swagger-ui/index.html")) }
        }
    }

    @Test
    fun `API still requires JWT while Swagger docs do not`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isUnauthorized() }
        }

        mockMvc.get("/v3/api-docs").andExpect {
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
