package com.example.platformmanagement.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI 3 / Swagger UI configuration (springdoc-openapi).
 *
 * UI (public, no token):  http://localhost:8080/swagger-ui.html
 * JSON (public, no token): http://localhost:8080/v3/api-docs
 *
 * API Try it out still requires a Microsoft Entra JWT via Swagger Authorize.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Platform Management Service API")
                    .description(
                        """
                        REST API for participants, caller identities, service offerings,
                        entitlements, and call consumption.

                        ### Using Swagger UI
                        - Opening this page does not require a token (docs are public).
                        - Calling /api does require a token: click Authorize, paste your
                          Entra access token (raw JWT, no Bearer prefix), then use Try it out.
                        - Tokens: backend/scripts/get-token-human.sh or get-token-mi.sh.

                        ### Authentication (API only)
                        Microsoft Entra ID (Azure AD) JWT bearer tokens.

                        App roles (JWT roles claim mapped to Spring ROLE_ authorities):
                        - System.Maintainer: full CRUD
                        - System.Reader: list/get all resources
                        - Entitlement.Reader: entitlement check only
                        - Consumption.Registrator: create consumption records
                        """.trimIndent()
                    )
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Platform Management Service")
                    )
                    .license(
                        License()
                            .name("Proprietary")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("/")
                        .description("Current host")
                )
            )
            .tags(
                listOf(
                    Tag().name("Auth").description("Authenticated principal / token claims"),
                    Tag().name("Participants").description("Organizations that consume platform services"),
                    Tag().name("Caller identities").description("Email / Entra client id / managed identity principals"),
                    Tag().name("Service offerings").description("Catalog of entitled services"),
                    Tag().name("Entitlements").description("Participant access rights and entitlement checks"),
                    Tag().name("Consumptions").description("Token / usage event records"),
                )
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        BEARER_JWT,
                        SecurityScheme()
                            .name(BEARER_JWT)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description(
                                "Optional for browsing docs. Required when executing /api operations. " +
                                    "Paste the raw Entra access token (without the Bearer prefix)."
                            )
                    )
            )
            // Marks /api operations as supporting Bearer auth so the Authorize button wires
            // Authorization: Bearer <token> into Try it out requests.
            .addSecurityItem(
                SecurityRequirement().addList(BEARER_JWT)
            )

    companion object {
        const val BEARER_JWT = "bearer-jwt"
    }
}
