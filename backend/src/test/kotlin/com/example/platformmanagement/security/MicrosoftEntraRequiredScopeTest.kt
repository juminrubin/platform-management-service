package com.example.platformmanagement.security

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
 * When `app.security.required-scope` is set, tokens must carry that scope/role
 * at the HTTP filter layer **and** the appropriate app role for method security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig::class)
@TestPropertySource(
    properties = [
        "app.security.permit-all=false",
        "app.security.required-scope=access_as_user"
    ]
)
class MicrosoftEntraRequiredScopeTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `JWT without required scope is forbidden`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("user-object-id")
                        token.claim("scp", "other_scope")
                        token.claim("roles", listOf(AppRoles.SYSTEM_MAINTAINER))
                    }
                    .authorities(
                        SimpleGrantedAuthority("SCOPE_other_scope"),
                        SimpleGrantedAuthority("ROLE_${AppRoles.SYSTEM_MAINTAINER}")
                    )
            )
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `JWT with required scope and System Maintainer role is allowed`() {
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("user-object-id")
                        token.claim("scp", "access_as_user")
                        token.claim("roles", listOf(AppRoles.SYSTEM_MAINTAINER))
                    }
                    .authorities(
                        SimpleGrantedAuthority("SCOPE_access_as_user"),
                        SimpleGrantedAuthority("ROLE_${AppRoles.SYSTEM_MAINTAINER}")
                    )
            )
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `JWT with required scope as app role and System Maintainer is allowed`() {
        // required-scope can be satisfied by ROLE_access_as_user as well as SCOPE_
        mockMvc.get("/api/v1/participants") {
            accept = MediaType.APPLICATION_JSON
            with(
                jwt()
                    .jwt { token ->
                        token.subject("app-object-id")
                        token.claim("roles", listOf("access_as_user", AppRoles.SYSTEM_MAINTAINER))
                    }
                    .authorities(
                        SimpleGrantedAuthority("ROLE_access_as_user"),
                        SimpleGrantedAuthority("ROLE_${AppRoles.SYSTEM_MAINTAINER}")
                    )
            )
        }.andExpect {
            status { isOk() }
        }
    }
}
