package com.example.participantapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class AppSecurityProperties(
    /** When true, API endpoints do not require a JWT (intended for local/dev only). */
    val permitAll: Boolean = false,
    /** Optional Microsoft Graph / custom API scope that must be present in the token (scp claim). */
    val requiredScope: String = "",
    val corsAllowedOrigins: List<String> = listOf("http://localhost:3000")
)
