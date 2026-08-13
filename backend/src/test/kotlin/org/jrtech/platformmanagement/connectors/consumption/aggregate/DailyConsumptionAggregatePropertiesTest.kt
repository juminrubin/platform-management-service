package org.jrtech.platformmanagement.connectors.consumption.aggregate

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DailyConsumptionAggregatePropertiesTest {

    @Test
    fun `resolved output prefix falls back when blank`() {
        assertThat(DailyConsumptionAggregateProperties().resolvedOutputBlobPrefix("curated"))
            .isEqualTo("curated")
        assertThat(DailyConsumptionAggregateProperties(outputBlobPrefix = "  ").resolvedOutputBlobPrefix("curated"))
            .isEqualTo("curated")
        assertThat(DailyConsumptionAggregateProperties().resolvedOutputBlobPrefix())
            .isEmpty()
    }

    @Test
    fun `resolved output prefix strips slashes and ignores fallback when set`() {
        assertThat(
            DailyConsumptionAggregateProperties(outputBlobPrefix = "  /daily/metrics/  ")
                .resolvedOutputBlobPrefix("curated")
        ).isEqualTo("daily/metrics")
    }
}
