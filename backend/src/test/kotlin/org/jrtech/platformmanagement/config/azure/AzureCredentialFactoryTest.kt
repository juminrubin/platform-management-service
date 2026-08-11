package org.jrtech.platformmanagement.config.azure

import com.azure.identity.ClientSecretCredential
import com.azure.identity.ManagedIdentityCredential
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AzureCredentialFactoryTest {

    @Test
    fun `service principal when client id and secret are both set`() {
        val props = AzureCredentialProperties(
            clientId = "app-id",
            clientSecret = "secret",
            tenantId = "tenant"
        )
        assertThat(AzureCredentialFactory.resolve(props))
            .isEqualTo(AzureCredentialFactory.Mode.SERVICE_PRINCIPAL)
        assertThat(AzureCredentialFactory.create(props, purpose = "test"))
            .isInstanceOf(ClientSecretCredential::class.java)
    }

    @Test
    fun `UAMI when client id is set without secret`() {
        val props = AzureCredentialProperties(clientId = "uami-or-wi-client-id")
        assertThat(AzureCredentialFactory.resolve(props))
            .isEqualTo(AzureCredentialFactory.Mode.UAMI)
        assertThat(AzureCredentialFactory.create(props, purpose = "test"))
            .isInstanceOf(ManagedIdentityCredential::class.java)
    }

    @Test
    fun `service principal requires tenant id at create time`() {
        val props = AzureCredentialProperties(
            clientId = "app-id",
            clientSecret = "secret"
        )
        assertThat(AzureCredentialFactory.resolve(props))
            .isEqualTo(AzureCredentialFactory.Mode.SERVICE_PRINCIPAL)
        assertThatThrownBy { AzureCredentialFactory.create(props, purpose = "test") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("AZURE_TENANT_ID")
    }

    @Test
    fun `secret alone falls through to SAMI`() {
        val props = AzureCredentialProperties(clientSecret = "secret-only")
        assertThat(AzureCredentialFactory.resolve(props))
            .isEqualTo(AzureCredentialFactory.Mode.SAMI)
    }

    @Test
    fun `SAMI when client id is empty`() {
        val props = AzureCredentialProperties()
        assertThat(AzureCredentialFactory.resolve(props))
            .isEqualTo(AzureCredentialFactory.Mode.SAMI)
        assertThat(AzureCredentialFactory.create(props, purpose = "test"))
            .isInstanceOf(ManagedIdentityCredential::class.java)
    }
}
