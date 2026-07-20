package org.jrtech.platformmanagement.security

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant

/**
 * Provides a JwtDecoder for tests that enable the resource-server filter chain
 * without contacting Microsoft Entra ID discovery endpoints.
 *
 * MockMvc `jwt()` post-processors populate the SecurityContext directly; this
 * decoder only satisfies bean wiring and rejects any real Bearer token parsing.
 */
@TestConfiguration
class TestJwtDecoderConfig {

    @Bean
    @Primary
    fun testJwtDecoder(): JwtDecoder = JwtDecoder { token ->
        if (token == "test-token") {
            Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("test-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("scp", "access_as_user")
                .build()
        } else {
            throw JwtException("Only mock SecurityContext JWTs are supported in tests")
        }
    }
}
