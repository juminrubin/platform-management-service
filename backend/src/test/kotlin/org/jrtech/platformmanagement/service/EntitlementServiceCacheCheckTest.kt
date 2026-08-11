package org.jrtech.platformmanagement.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jrtech.platformmanagement.cache.CachedCallerRegistration
import org.jrtech.platformmanagement.cache.CachedEntitlement
import org.jrtech.platformmanagement.cache.CachedServiceOffering
import org.jrtech.platformmanagement.cache.EntitlementCheckCache
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.jrtech.platformmanagement.TestAudit

@ExtendWith(MockitoExtension::class)
class EntitlementServiceCacheCheckTest {

    @Mock
    private lateinit var entitlementRepository: ParticipantServiceEntitlementRepository

    @Mock
    private lateinit var callerRegistrationRepository: ParticipantCallerRegistrationRepository

    @Mock
    private lateinit var participantService: ParticipantService

    @Mock
    private lateinit var serviceOfferingService: ServiceOfferingService

    @Mock
    private lateinit var entitlementCheckCache: EntitlementCheckCache

    @InjectMocks
    private lateinit var entitlementService: EntitlementService

    @BeforeEach
    fun useCache() {
        whenever(entitlementCheckCache.isUsableForChecks()).thenReturn(true)
    }

    @Test
    fun `check from cache returns ALLOWED without repository calls`() {
        whenever(entitlementCheckCache.findService("gpt")).thenReturn(
            CachedServiceOffering(id = "gpt", name = "GPT", active = true)
        )
        whenever(entitlementCheckCache.findCaller("a@x.com")).thenReturn(
            CachedCallerRegistration(
                callerId = "a@x.com",
                participantId = "p1",
                participantName = "P",
                status = CallerRegistrationStatus.ACTIVE
            )
        )
        whenever(entitlementCheckCache.findEntitlement("p1", "gpt")).thenReturn(
            cachedEntitlement()
        )

        val result = entitlementService.checkByCallerAndService(
            "a@x.com",
            "gpt",
            fromDate = LocalDate.of(2025, 6, 1)
        )

        assertThat(result.allowed).isTrue()
        assertThat(result.reason).isEqualTo("ALLOWED")
        assertThat(result.participantId).isEqualTo("p1")
        assertThat(result.entitlement?.serviceOfferingName).isEqualTo("GPT")
        verify(serviceOfferingService, never()).getEntity("gpt")
        verify(callerRegistrationRepository, never()).findByCallerIdWithParticipant("a@x.com")
        verify(entitlementRepository, never()).findByParticipantId("p1")
    }

    @Test
    fun `check from cache returns CALLER_NOT_FOUND`() {
        whenever(entitlementCheckCache.findService("gpt")).thenReturn(
            CachedServiceOffering(id = "gpt", name = "GPT", active = true)
        )
        whenever(entitlementCheckCache.findCaller("missing")).thenReturn(null)

        val result = entitlementService.checkByCallerAndService(
            "missing",
            "gpt",
            fromDate = LocalDate.of(2025, 6, 1)
        )
        assertThat(result.allowed).isFalse()
        assertThat(result.reason).isEqualTo("CALLER_NOT_FOUND")
    }

    @Test
    fun `check from cache throws when service missing`() {
        whenever(entitlementCheckCache.findService("missing")).thenReturn(null)
        assertThatThrownBy {
            entitlementService.checkByCallerAndService(
                "a@x.com",
                "missing",
                fromDate = LocalDate.of(2025, 6, 1)
            )
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    private fun cachedEntitlement() = CachedEntitlement(
        id = UUID.randomUUID(),
        participantId = "p1",
        participantName = "P",
        serviceOfferingId = "gpt",
        serviceOfferingName = "GPT",
        status = EntitlementStatus.ACTIVE,
        validFrom = LocalDate.of(2025, 1, 1),
        validTo = LocalDate.of(2025, 12, 31),
        config = "{}",
        notes = null,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        createdBy = TestAudit.BY,
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedBy = TestAudit.BY
    )
}
