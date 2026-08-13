package org.jrtech.platformmanagement.connectors.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConsumptionBlobPropertiesTest {

    @Test
    fun `resolved input prefixes default to container root`() {
        assertThat(ConsumptionBlobProperties().resolvedInputBlobPrefixes()).containsExactly("")
        assertThat(ConsumptionBlobProperties().resolvedOutputBlobPrefix()).isEmpty()
    }

    @Test
    fun `resolved input prefixes merge list and CSV and strip slashes`() {
        assertThat(
            ConsumptionBlobProperties(inputBlobPrefix = "/eh-capture/").resolvedInputBlobPrefixes()
        ).containsExactly("eh-capture")
        assertThat(
            ConsumptionBlobProperties(
                inputBlobPrefixes = listOf("a", "b"),
                inputBlobPrefix = "b,c"
            ).resolvedInputBlobPrefixes()
        ).containsExactly("a", "b", "c")
        assertThat(
            ConsumptionBlobProperties(outputBlobPrefix = "  curated/metrics  ").resolvedOutputBlobPrefix()
        ).isEqualTo("curated/metrics")
    }

    @Test
    fun `blobEndpointUrl builds public Azure endpoint from account name`() {
        assertThat(ConsumptionBlobProperties(storageAccountName = "MyAcct").blobEndpointUrl())
            .isEqualTo("https://myacct.blob.core.windows.net")
        assertThat(ConsumptionBlobProperties().blobEndpointUrl()).isEmpty()
    }

    @Test
    fun `isConfigured requires containers plus account name or connection string`() {
        assertThat(ConsumptionBlobProperties().isConfigured()).isFalse()
        assertThat(
            ConsumptionBlobProperties(
                inputContainer = "in",
                outputContainer = "out",
                storageAccountName = "acct"
            ).isConfigured()
        ).isTrue()
        assertThat(
            ConsumptionBlobProperties(
                inputContainer = "in",
                outputContainer = "out",
                connectionString = "UseDevelopmentStorage=true"
            ).isConfigured()
        ).isTrue()
        assertThat(
            ConsumptionBlobProperties(
                inputContainer = "in",
                storageAccountName = "acct"
            ).isConfigured()
        ).isFalse()
    }
}
