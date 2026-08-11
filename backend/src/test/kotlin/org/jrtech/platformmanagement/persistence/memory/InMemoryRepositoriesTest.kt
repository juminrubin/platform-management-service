package org.jrtech.platformmanagement.persistence.memory

import org.jrtech.platformmanagement.TestAudit
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.persistence.InMemoryPlatformStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InMemoryRepositoriesTest {

    private lateinit var store: InMemoryPlatformStore
    private lateinit var participants: InMemoryParticipantRepository
    private lateinit var services: InMemoryServiceOfferingRepository
    private lateinit var callers: InMemoryParticipantCallerRegistrationRepository
    private lateinit var entitlements: InMemoryParticipantServiceEntitlementRepository
    private lateinit var consumptions: InMemoryParticipantCallConsumptionRepository

    @BeforeEach
    fun setUp() {
        store = InMemoryPlatformStore()
        participants = InMemoryParticipantRepository(store)
        services = InMemoryServiceOfferingRepository(store)
        callers = InMemoryParticipantCallerRegistrationRepository(store)
        entitlements = InMemoryParticipantServiceEntitlementRepository(store)
        consumptions = InMemoryParticipantCallConsumptionRepository(store)
    }

    @Test
    fun `participant repository filters and uniqueness helpers`() {
        participants.save(p("p1", "Alpha", ParticipantStatus.ACTIVE))
        participants.save(p("p2", "Beta", ParticipantStatus.SUSPENDED))

        assertThat(participants.findAll()).hasSize(2)
        assertThat(participants.findByStatus(ParticipantStatus.SUSPENDED).map { it.id }).containsExactly("p2")
        assertThat(participants.findByName("Alpha")?.id).isEqualTo("p1")
        assertThat(participants.existsByName("Alpha")).isTrue()
        assertThat(participants.existsByNameAndIdNot("Alpha", "p1")).isFalse()
        assertThat(participants.existsByNameAndIdNot("Alpha", "other")).isTrue()
        assertThat(participants.count()).isEqualTo(2)

        participants.deleteById("p1")
        assertThat(participants.findById("p1")).isNull()
        assertThat(participants.existsById("p1")).isFalse()
    }

    @Test
    fun `service offering repository filters by active and category`() {
        services.save(so("s1", "LLM", active = true))
        services.save(so("s2", "SPEECH", active = false))
        services.save(so("s3", "llm", active = true))

        assertThat(services.findByActiveTrue().map { it.id }).containsExactlyInAnyOrder("s1", "s3")
        assertThat(services.findByCategory("LLM").map { it.id }).containsExactlyInAnyOrder("s1", "s3")
        assertThat(services.findAll()).hasSize(3)
        assertThat(services.count()).isEqualTo(3)
        services.deleteById("s2")
        assertThat(services.existsById("s2")).isFalse()
    }

    @Test
    fun `caller repository hydrates participant and filters`() {
        val p1 = participants.save(p("p1", "P1"))
        callers.save(caller("c1", p1, CallerRegistrationStatus.ACTIVE))
        callers.save(caller("c2", p1, CallerRegistrationStatus.INACTIVE))

        assertThat(callers.findByCallerIdWithParticipant("c1")?.participant?.name).isEqualTo("P1")
        assertThat(callers.findByParticipantId("p1")).hasSize(2)
        assertThat(callers.findByStatus(CallerRegistrationStatus.INACTIVE)).hasSize(1)
        assertThat(callers.findAllWithParticipant()).hasSize(2)
        assertThat(callers.existsByCallerId("c1")).isTrue()
        assertThat(callers.count()).isEqualTo(2)
        callers.deleteById("c1")
        assertThat(callers.existsById("c1")).isFalse()
    }

    @Test
    fun `entitlement repository indexes and active-valid filter`() {
        val p1 = participants.save(p("p1", "P1"))
        val s1 = services.save(so("gpt", "LLM"))
        val s2 = services.save(so("stt", "SPEECH"))
        val today = LocalDate.now()

        val e1 = entitlements.save(
            ent(p1, s1, EntitlementStatus.ACTIVE, today.minusDays(1), today.plusDays(10))
        )
        entitlements.save(
            ent(p1, s2, EntitlementStatus.PENDING, today.minusDays(1), today.plusDays(10))
        )

        assertThat(entitlements.findByIdWithRelations(e1.id)?.serviceOffering?.id).isEqualTo("gpt")
        assertThat(entitlements.findByParticipantId("p1")).hasSize(2)
        assertThat(entitlements.findByServiceOfferingId("gpt")).hasSize(1)
        assertThat(entitlements.findByStatus(EntitlementStatus.PENDING)).hasSize(1)
        assertThat(entitlements.existsByParticipantIdAndServiceOfferingId("p1", "gpt")).isTrue()
        assertThat(
            entitlements.findActiveAndValidAsOf(today, EntitlementStatus.ACTIVE).map { it.serviceOffering.id }
        ).containsExactly("gpt")
        assertThat(entitlements.count()).isEqualTo(2)

        entitlements.deleteById(e1.id)
        assertThat(entitlements.existsById(e1.id)).isFalse()
        assertThat(entitlements.existsByParticipantIdAndServiceOfferingId("p1", "gpt")).isFalse()
    }

    @Test
    fun `consumption repository indexes source ref and filters`() {
        val p1 = participants.save(p("p1", "P1"))
        val s1 = services.save(so("gpt", "LLM"))
        val c1 = callers.save(caller("user@x.com", p1))
        val id = UUID.randomUUID()
        val saved = consumptions.save(
            ParticipantCallConsumption(
                id = id,
                callerRegistration = c1,
                serviceOffering = s1,
                sourceRefId = "req-1",
                consumptionData = """{"n":1}""",
                capturedAt = Instant.parse("2026-01-01T00:00:00Z"),
                createdAt = Instant.parse("2026-01-01T00:00:01Z")
            )
        )
        assertThat(saved.callerRegistration.participant.name).isEqualTo("P1")
        assertThat(consumptions.existsBySourceRefId("req-1")).isTrue()
        assertThat(consumptions.findBySourceRefIdWithRelations("req-1")?.id).isEqualTo(id)
        assertThat(consumptions.findByIdWithRelations(id)?.serviceOffering?.id).isEqualTo("gpt")
        assertThat(consumptions.findByCallerId("user@x.com")).hasSize(1)
        assertThat(consumptions.findByServiceOfferingId("gpt")).hasSize(1)
        assertThat(consumptions.findAllWithRelations()).hasSize(1)
        assertThat(consumptions.count()).isEqualTo(1)

        consumptions.deleteById(id)
        assertThat(consumptions.existsById(id)).isFalse()
        assertThat(consumptions.existsBySourceRefId("req-1")).isFalse()
    }

    @Test
    fun `store clear empties all maps`() {
        participants.save(p("p1", "P1"))
        services.save(so("s1", "LLM"))
        store.clear()
        assertThat(participants.count()).isZero()
        assertThat(services.count()).isZero()
        assertThat(store.participants).isEmpty()
    }

    private fun p(id: String, name: String, status: ParticipantStatus = ParticipantStatus.ACTIVE) =
        Participant(
            id = id,
            name = name,
            status = status,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY
        )

    private fun so(id: String, category: String, active: Boolean = true) =
        ServiceOffering(
            id = id,
            name = id,
            category = category,
            active = active,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY
        )

    private fun caller(
        id: String,
        participant: Participant,
        status: CallerRegistrationStatus = CallerRegistrationStatus.ACTIVE
    ) = ParticipantCallerRegistration(
        callerId = id,
        participant = participant,
        status = status,
        createdBy = TestAudit.BY,
        updatedBy = TestAudit.BY
    )

    private fun ent(
        participant: Participant,
        offering: ServiceOffering,
        status: EntitlementStatus,
        from: LocalDate,
        to: LocalDate?
    ) = ParticipantServiceEntitlement(
        participant = participant,
        serviceOffering = offering,
        status = status,
        validFrom = from,
        validTo = to,
        createdBy = TestAudit.BY,
        updatedBy = TestAudit.BY
    )
}
