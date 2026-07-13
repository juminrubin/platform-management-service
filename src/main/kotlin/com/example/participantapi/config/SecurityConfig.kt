package com.example.participantapi.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Secures the API as an OAuth2 resource server validating Microsoft Entra ID (Azure AD) JWTs.
 *
 * Tokens are obtained by clients from:
 *   https://login.microsoftonline.com/{tenant-id}/oauth2/v2.0/token
 *
 * and presented as: Authorization: Bearer &lt;access_token&gt;
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AppSecurityProperties::class)
class SecurityConfig(
    private val securityProperties: AppSecurityProperties
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/h2-console/**",
                        "/error"
                    ).permitAll()

                if (securityProperties.permitAll) {
                    auth.anyRequest().permitAll()
                } else {
                    val scope = securityProperties.requiredScope.trim()
                    if (scope.isNotEmpty()) {
                        // Require a specific delegated scope (scp) or app role
                        auth.requestMatchers("/api/**")
                            .hasAnyAuthority("SCOPE_$scope", "ROLE_$scope")
                    }
                    auth.anyRequest().authenticated()
                }
            }
            .headers { headers ->
                // Allow H2 console frames in local/dev
                headers.frameOptions { it.sameOrigin() }
            }

        if (!securityProperties.permitAll) {
            http.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(microsoftJwtAuthenticationConverter())
                }
            }
        }

        return http.build()
    }

    /**
     * Maps Microsoft Entra ID claims to Spring Security authorities:
     * - scp / scope  → SCOPE_* (delegated permissions)
     * - roles        → ROLE_*  (app roles assigned in the API app registration)
     */
    @Bean
    fun microsoftJwtAuthenticationConverter(): Converter<Jwt, out AbstractAuthenticationToken> {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt -> extractAuthorities(jwt) }
        return converter
    }

    private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = mutableSetOf<GrantedAuthority>()

        // Delegated scopes: space-delimited string in "scp" (v1/v2) or "scope"
        val scopeClaim = jwt.getClaimAsString("scp")
            ?: jwt.getClaimAsString("scope")
            ?: ""
        scopeClaim.split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { authorities += SimpleGrantedAuthority("SCOPE_$it") }

        // App roles: array claim "roles"
        val roles = jwt.getClaimAsStringList("roles") ?: emptyList()
        roles.forEach { role ->
            val normalized = if (role.startsWith("ROLE_")) role else "ROLE_$role"
            authorities += SimpleGrantedAuthority(normalized)
        }

        return authorities
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = securityProperties.corsAllowedOrigins
            allowedMethods = listOf(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
            )
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
