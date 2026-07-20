package com.example.platformmanagement.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StatusConvertersTest {

    @Test
    fun `participant status converter round-trips and nulls`() {
        val converter = ParticipantStatusConverter()
        assertThat(converter.convertToDatabaseColumn(ParticipantStatus.SUSPENDED)).isEqualTo("SUSPENDED")
        assertThat(converter.convertToEntityAttribute("INACTIVE")).isEqualTo(ParticipantStatus.INACTIVE)
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `entitlement status converter round-trips and nulls`() {
        val converter = EntitlementStatusConverter()
        assertThat(converter.convertToDatabaseColumn(EntitlementStatus.PENDING)).isEqualTo("PENDING")
        assertThat(converter.convertToEntityAttribute("REVOKED")).isEqualTo(EntitlementStatus.REVOKED)
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `caller registration status converter round-trips and nulls`() {
        val converter = CallerRegistrationStatusConverter()
        assertThat(converter.convertToDatabaseColumn(CallerRegistrationStatus.ACTIVE)).isEqualTo("ACTIVE")
        assertThat(converter.convertToEntityAttribute("INACTIVE")).isEqualTo(CallerRegistrationStatus.INACTIVE)
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }
}
