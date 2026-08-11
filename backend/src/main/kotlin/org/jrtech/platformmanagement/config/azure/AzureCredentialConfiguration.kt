package org.jrtech.platformmanagement.config.azure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Registers shared [AzureCredentialProperties] (`app.azure.credential`).
 *
 * Credentials are built on demand via [AzureCredentialFactory] (not as an eager bean).
 */
@Configuration
@EnableConfigurationProperties(AzureCredentialProperties::class)
class AzureCredentialConfiguration
