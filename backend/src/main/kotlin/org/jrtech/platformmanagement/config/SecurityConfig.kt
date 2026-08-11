package org.jrtech.platformmanagement.config

import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.security.EntraHumanAuthorizationService
import org.jrtech.platformmanagement.security.JwtAuthorityMapper
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.net.InetAddress

/**
 * Secures the API as an OAuth2 resource server validating Microsoft Entra ID (Azure AD) JWTs.
 *
 * Clients obtain tokens from login.microsoftonline.com and present them as:
 * Authorization: Bearer access_token
 *
 * JWT issuer/audience come from application.yml (APP_AZURE_TENANT_ID, APP_API_CLIENT_ID).
 * app.security.permit-all=true is only for automated tests, never for runtime.
 *
 * OpenAPI / Swagger UI is on a separate public filter chain (no JWT required to open docs).
 * Calling /api from Swagger "Try it out" still requires a Bearer token via Authorize.
 *
 * H2 console paths are allowed only from loopback (localhost); remote clients get 403.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AppSecurityProperties::class)
class SecurityConfig(
    private val securityProperties: AppSecurityProperties,
    private val humanAuthorization: ObjectProvider<EntraHumanAuthorizationService>
) {

    private val problemMapper = ObjectMapper()

    /**
     * Public docs chain — no OAuth2 resource-server filter, no token required.
     * Covers OpenAPI JSON and Swagger UI static assets only.
     */
    @Bean
    @Order(1)
    fun swaggerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(
                "/v3/api-docs",
                "/v3/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/webjars/**",
            )
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            // Explicitly no oauth2ResourceServer on this chain
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
        return http.build()
    }

    /**
     * API / app chain — JWT required for /api (unless test permit-all).
     * Swagger "Authorize" + Try it out sends Bearer tokens here.
     */
    @Bean
    @Order(2)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
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
                        "/error"
                    ).permitAll()
                    // H2 in-memory console: loopback only
                    .requestMatchers(h2ConsoleFromLocalhost()).permitAll()
                    .requestMatchers("/h2-console", "/h2-console/**").denyAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                if (securityProperties.permitAll) {
                    auth.anyRequest().permitAll()
                } else {
                    // /auth/me is for any valid JWT principal (no app-role / required-scope gate)
                    auth.requestMatchers("/api/v1/auth/**").authenticated()

                    val scope = securityProperties.requiredScope.trim()
                    if (scope.isNotEmpty()) {
                        // Optional tenant-wide scope gate on remaining API routes.
                        // Accepts short names (access_as_user) and full URI scopes (api://…/access_as_user).
                        auth.requestMatchers("/api/**").access { authentication, _ ->
                            val authn = authentication.get()
                            if (!authn.isAuthenticated) {
                                return@access AuthorizationDecision(false)
                            }
                            val names = authn.authorities.mapNotNull { it.authority }
                            AuthorizationDecision(JwtAuthorityMapper.matchesRequiredScope(names, scope))
                        }
                    } else {
                        auth.requestMatchers("/api/**").authenticated()
                    }
                    auth.anyRequest().authenticated()
                }
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(problemDetailAuthenticationEntryPoint())
                    .accessDeniedHandler(problemDetailAccessDeniedHandler())
            }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() }
            }

        if (!securityProperties.permitAll) {
            http.oauth2ResourceServer { oauth2 ->
                oauth2
                    .authenticationEntryPoint(problemDetailAuthenticationEntryPoint())
                    .accessDeniedHandler(problemDetailAccessDeniedHandler())
                    .jwt { jwt ->
                        jwt.jwtAuthenticationConverter(microsoftJwtAuthenticationConverter())
                    }
            }
        }

        return http.build()
    }

    /**
     * H2 console only when TCP peer is loopback (127.0.0.1 / ::1).
     * Uses remoteAddr only (not X-Forwarded-For) so proxies cannot spoof access.
     */
    private fun h2ConsoleFromLocalhost(): RequestMatcher =
        RequestMatcher { request ->
            isH2ConsolePath(request.requestURI) && isLoopbackAddress(request.remoteAddr)
        }

    private fun isH2ConsolePath(uri: String?): Boolean {
        if (uri.isNullOrBlank()) {
            return false
        }
        return uri == "/h2-console" || uri.startsWith("/h2-console/")
    }

    private fun isLoopbackAddress(remoteAddr: String?): Boolean {
        if (remoteAddr.isNullOrBlank()) {
            return false
        }
        return try {
            InetAddress.getByName(remoteAddr).isLoopbackAddress
        } catch (_: Exception) {
            false
        }
    }

    @Bean
    fun microsoftJwtAuthenticationConverter(): Converter<Jwt, out AbstractAuthenticationToken> {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            // Prefer human Platform-System-* membership → ROLE_* mapping when available;
            // otherwise fall back to pure JWT claim mapping (technical users / tests).
            val human = humanAuthorization.getIfAvailable()
            if (human != null) {
                human.extractAuthorities(jwt)
            } else {
                JwtAuthorityMapper.extractAuthorities(jwt, securityProperties)
            }
        }
        return converter
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
            exposedHeaders = listOf("WWW-Authenticate")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    private fun problemDetailAuthenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request, response, authException ->
            writeProblem(
                request = request,
                response = response,
                status = HttpStatus.UNAUTHORIZED,
                detail = authException.message ?: "Full authentication is required to access this resource",
                title = "Unauthorized"
            )
        }

    private fun problemDetailAccessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { request, response, accessDeniedException ->
            writeProblem(
                request = request,
                response = response,
                status = HttpStatus.FORBIDDEN,
                detail = accessDeniedException.message ?: "Access is denied",
                title = "Forbidden"
            )
        }

    private fun writeProblem(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        detail: String,
        title: String
    ) {
        if (response.isCommitted) {
            return
        }
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer")
        }
        val body = linkedMapOf(
            "type" to "about:blank",
            "title" to title,
            "status" to status.value(),
            "detail" to detail,
            "instance" to request.requestURI,
            "timestamp" to UtcTimestamps.now().toString()
        )
        problemMapper.writeValue(response.outputStream, body)
    }
}
