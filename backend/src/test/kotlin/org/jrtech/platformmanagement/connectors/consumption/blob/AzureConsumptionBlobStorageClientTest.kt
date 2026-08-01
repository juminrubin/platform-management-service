package org.jrtech.platformmanagement.connectors.consumption.blob

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AzureConsumptionBlobStorageClientTest {

    @Test
    fun `accepts HH_mm_ss avro file names`() {
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("14_30_00.avro")).isTrue()
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("00_00_00.avro")).isTrue()
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("2024/07/01/09_05_33.avro")).isTrue()
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("capture/2024/07/01/23_59_59.AVRO")).isTrue()
    }

    @Test
    fun `rejects non matching names`() {
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("file.avro")).isFalse()
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("14-30-00.avro")).isFalse()
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("14_30_00.json")).isFalse()
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("2024/07/01/")).isFalse()
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("14_30_00.avro.bak")).isFalse()
        assertThat(AzureConsumptionBlobStorageClient.isConsumptionAvroBlob("1_2_3.avro")).isFalse()
    }
}
