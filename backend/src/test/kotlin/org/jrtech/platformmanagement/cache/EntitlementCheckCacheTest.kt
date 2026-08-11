package org.jrtech.platformmanagement.cache

import org.springframework.boot.test.context.SpringBootTest

import org.assertj.core.api.Assertions.assertThat
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.jrtech.platformmanagement.TestAudit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest
@ActiveProfiles("test")
class EntitlementCheckCacheTest @Autowired constructor(
    private val cache: EntitlementCheckCache,
    private val serviceOfferingRepository: ServiceOfferingRepository,
    private val participantRepository: ParticipantRepository,
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val entitlementRepository: ParticipantServiceEntitlementRepository
) {

    @Test
    fun `refresh builds concurrent maps for services callers and entitlements`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val offering = serviceOfferingRepository.save(
            ServiceOffering(
                id = "cache-gpt",
                name = "Cache GPT",
                category = "LLM",
                active = true,
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        val participant = participantRepository.save(
            Participant(
                id = "cache-p1",
                name = "Cache Participant",
                status = ParticipantStatus.ACTIVE,
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        callerRegistrationRepository.save(
            ParticipantCallerRegistration(
                callerId = "cache-user@example.com",
                participant = participant,
                status = CallerRegistrationStatus.ACTIVE,
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        entitlementRepository.save(
            ParticipantServiceEntitlement(
                participant = participant,
                serviceOffering = offering,
                status = EntitlementStatus.ACTIVE,
                validFrom = today.minusDays(1),
                validTo = today.plusYears(1),
                config = "{}",
                notes = "cached",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )

        val status = cache.refresh(triggeredBy = "test")

        assertThat(status.loaded).isTrue()
        assertThat(status.entitlementsAsOf).isEqualTo(today)
        assertThat(status.serviceCount).isGreaterThanOrEqualTo(1)
        assertThat(status.callerCount).isGreaterThanOrEqualTo(1)
        assertThat(status.entitlementCount).isGreaterThanOrEqualTo(1)
        assertThat(status.lastRefreshBy).isEqualTo("test")
        assertThat(status.lastError).isNull()
        assertThat(cache.isUsableForChecks()).isTrue()

        assertThat(cache.findService("cache-gpt")).isNotNull
        assertThat(cache.findCaller("cache-user@example.com")!!.participantId).isEqualTo("cache-p1")
        val entitlement = cache.findEntitlement("cache-p1", "cache-gpt")
        assertThat(entitlement).isNotNull
        assertThat(entitlement!!.status).isEqualTo(EntitlementStatus.ACTIVE)
        assertThat(entitlement.notes).isEqualTo("cached")
        assertThat(entitlement.toResponse().serviceOfferingName).isEqualTo("Cache GPT")
    }

    @Test
    fun `refresh loads only ACTIVE entitlements valid as of today UTC`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val offeringActive = serviceOfferingRepository.save(
            ServiceOffering(
                id = "so-active",
                name = "Active SO",
                category = "LLM",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        val offeringPending = serviceOfferingRepository.save(
            ServiceOffering(
                id = "so-pending",
                name = "Pending SO",
                category = "LLM",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        val offeringFuture = serviceOfferingRepository.save(
            ServiceOffering(
                id = "so-future",
                name = "Future SO",
                category = "LLM",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        val offeringExpired = serviceOfferingRepository.save(
            ServiceOffering(
                id = "so-expired",
                name = "Expired SO",
                category = "LLM",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        val participant = participantRepository.save(
            Participant(
                id = "cache-filter-p",
                name = "Filter Participant",
                status = ParticipantStatus.ACTIVE,
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )

        fun saveEntitlement(
            offering: ServiceOffering,
            status: EntitlementStatus,
            validFrom: LocalDate,
            validTo: LocalDate?
        ) {
            entitlementRepository.save(
                ParticipantServiceEntitlement(
                    participant = participant,
                    serviceOffering = offering,
                    status = status,
                    validFrom = validFrom,
                    validTo = validTo,
                    config = "{}",
                    createdBy = TestAudit.BY,
                    updatedBy = TestAudit.BY
                )
            )
        }

        saveEntitlement(offeringActive, EntitlementStatus.ACTIVE, today.minusDays(10), today.plusDays(10))
        saveEntitlement(offeringPending, EntitlementStatus.PENDING, today.minusDays(10), today.plusDays(10))
        saveEntitlement(offeringFuture, EntitlementStatus.ACTIVE, today.plusDays(1), today.plusDays(30))
        saveEntitlement(offeringExpired, EntitlementStatus.ACTIVE, today.minusDays(30), today.minusDays(1))

        cache.refresh(triggeredBy = "filter-test")

        assertThat(cache.findEntitlement(participant.id, offeringActive.id)).isNotNull
        assertThat(cache.findEntitlement(participant.id, offeringPending.id)).isNull()
        assertThat(cache.findEntitlement(participant.id, offeringFuture.id)).isNull()
        assertThat(cache.findEntitlement(participant.id, offeringExpired.id)).isNull()
    }

    @Test
    fun `refresh is idempotent and replaces previous snapshot`() {
        serviceOfferingRepository.save(
            ServiceOffering(
                id = "only-one",
                name = "One",
                category = "LLM",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        cache.refresh(triggeredBy = "first")
        assertThat(cache.findService("only-one")).isNotNull

        serviceOfferingRepository.save(
            ServiceOffering(
                id = "second",
                name = "Two",
                category = "LLM",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        cache.refresh(triggeredBy = "second")

        assertThat(cache.findService("only-one")).isNotNull
        assertThat(cache.findService("second")).isNotNull
        assertThat(cache.status().serviceCount).isGreaterThanOrEqualTo(2)
        assertThat(cache.status().lastRefreshBy).isEqualTo("second")
    }
}
