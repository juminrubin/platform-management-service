package org.jrtech.platformmanagement.security

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
