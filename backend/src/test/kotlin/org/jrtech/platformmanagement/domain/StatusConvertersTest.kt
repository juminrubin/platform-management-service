package org.jrtech.platformmanagement.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StatusConvertersTest {

    @Test
    fun `participant status parsing`() {
        assertThat(StatusParsing.participantStatus("SUSPENDED")).isEqualTo(ParticipantStatus.SUSPENDED)
        assertThat(StatusParsing.participantStatus("inactive")).isEqualTo(ParticipantStatus.INACTIVE)
        assertThat(StatusParsing.participantStatus(null)).isEqualTo(ParticipantStatus.ACTIVE)
        assertThat(StatusParsing.participantStatus("nope")).isEqualTo(ParticipantStatus.ACTIVE)
    }

    @Test
    fun `entitlement status parsing`() {
        assertThat(StatusParsing.entitlementStatus("PENDING")).isEqualTo(EntitlementStatus.PENDING)
        assertThat(StatusParsing.entitlementStatus("revoked")).isEqualTo(EntitlementStatus.REVOKED)
        assertThat(StatusParsing.entitlementStatus(null)).isEqualTo(EntitlementStatus.PENDING)
    }

    @Test
    fun `caller registration status parsing`() {
        assertThat(StatusParsing.callerRegistrationStatus("ACTIVE")).isEqualTo(CallerRegistrationStatus.ACTIVE)
        assertThat(StatusParsing.callerRegistrationStatus("inactive")).isEqualTo(CallerRegistrationStatus.INACTIVE)
        assertThat(StatusParsing.callerRegistrationStatus(null)).isEqualTo(CallerRegistrationStatus.ACTIVE)
    }
}
