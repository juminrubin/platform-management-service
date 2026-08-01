package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.dto.CreateEntitlementRequest
import org.jrtech.platformmanagement.dto.UpdateEntitlementRequest
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID
import org.jrtech.platformmanagement.TestAudit

@ExtendWith(MockitoExtension::class)
class EntitlementServiceTest {

    @Mock
    private lateinit var entitlementRepository: ParticipantServiceEntitlementRepository

    @Mock
    private lateinit var callerRegistrationRepository: ParticipantCallerRegistrationRepository

    @Mock
    private lateinit var participantService: ParticipantService

    @Mock
    private lateinit var serviceOfferingService: ServiceOfferingService

    @InjectMocks
    private lateinit var entitlementService: EntitlementService

    @Test
    fun `findAll without filters uses findAllWithRelations`() {
        whenever(entitlementRepository.findAllWithRelations()).thenReturn(listOf(entitlement()))
        assertThat(entitlementService.findAll(null, null, null)).hasSize(1)
    }

    @Test
    fun `findAll filters by participant and status`() {
        val active = entitlement(status = EntitlementStatus.ACTIVE)
        val pending = entitlement(status = EntitlementStatus.PENDING)
        whenever(entitlementRepository.findByParticipantId("p1")).thenReturn(listOf(active, pending))
        assertThat(entitlementService.findAll("p1", null, EntitlementStatus.ACTIVE)).hasSize(1)
    }

    @Test
    fun `findAll by service offering`() {
        whenever(entitlementRepository.findByServiceOfferingId("gpt")).thenReturn(listOf(entitlement()))
        assertThat(entitlementService.findAll(null, "gpt", null)).hasSize(1)
    }

    @Test
    fun `delete removes existing entitlement`() {
        val id = UUID.randomUUID()
        whenever(entitlementRepository.existsById(id)).thenReturn(true)
        entitlementService.delete(id)
        verify(entitlementRepository).deleteById(id)
    }

    @Test
    fun `delete throws when missing`() {
        val id = UUID.randomUUID()
        whenever(entitlementRepository.existsById(id)).thenReturn(false)
        assertThatThrownBy { entitlementService.delete(id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `update throws when missing`() {
        val id = UUID.randomUUID()
        whenever(entitlementRepository.findByIdWithRelations(id)).thenReturn(null)
        assertThatThrownBy {
            entitlementService.update(
                id,
                UpdateEntitlementRequest(
                    status = EntitlementStatus.ACTIVE,
                    validFrom = LocalDate.of(2025, 1, 1),
                    validTo = null,
                    config = "{}",
                    notes = null
                )
            )
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `check requires service offering id`() {
        assertThatThrownBy {
            entitlementService.checkByCallerAndService("a@x.com", "  ")
        }.isInstanceOf(BadRequestException::class.java)
    }

    @Test
    fun `check requires caller id`() {
        assertThatThrownBy {
            entitlementService.checkByCallerAndService(
                "  ",
                "gpt",
                fromDate = LocalDate.of(2024, 6, 1)
            )
        }.isInstanceOf(BadRequestException::class.java)
    }

    @Test
    fun `check rejects untilDate before fromDate`() {
        assertThatThrownBy {
            entitlementService.checkByCallerAndService(
                "a@x.com",
                "gpt",
                fromDate = LocalDate.of(2025, 6, 1),
                untilDate = LocalDate.of(2025, 1, 1)
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("untilDate")
    }

    @Test
    fun `check returns CALLER_NOT_FOUND`() {
        whenever(serviceOfferingService.getEntity("gpt")).thenReturn(offering("gpt"))
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("missing@x.com")).thenReturn(null)
        val result = entitlementService.checkByCallerAndService(
            "missing@x.com",
            "gpt",
            fromDate = LocalDate.of(2024, 6, 1)
        )
        assertThat(result.allowed).isFalse()
        assertThat(result.reason).isEqualTo("CALLER_NOT_FOUND")
        assertThat(result.callerId).isEqualTo("missing@x.com")
        assertThat(result.fromDate).isEqualTo(LocalDate.of(2024, 6, 1))
        assertThat(result.untilDate).isEqualTo(LocalDate.of(2024, 6, 1))
    }

    @Test
    fun `check returns CALLER_NOT_ACTIVE`() {
        val caller = ParticipantCallerRegistration(
            callerId = "a@x.com",
            participant = participant("p1"),
            status = CallerRegistrationStatus.INACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        whenever(serviceOfferingService.getEntity("gpt")).thenReturn(offering("gpt"))
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("a@x.com")).thenReturn(caller)
        val result = entitlementService.checkByCallerAndService(
            "a@x.com",
            "gpt",
            fromDate = LocalDate.of(2024, 6, 1)
        )
        assertThat(result.allowed).isFalse()
        assertThat(result.reason).isEqualTo("CALLER_NOT_ACTIVE")
    }

    @Test
    fun `check returns NO_ENTITLEMENT`() {
        val caller = activeCaller()
        whenever(serviceOfferingService.getEntity("gpt")).thenReturn(offering("gpt"))
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("a@x.com")).thenReturn(caller)
        whenever(entitlementRepository.findByParticipantId("p1")).thenReturn(emptyList())
        val result = entitlementService.checkByCallerAndService(
            "a@x.com",
            "gpt",
            fromDate = LocalDate.of(2024, 6, 1)
        )
        assertThat(result.allowed).isFalse()
        assertThat(result.reason).isEqualTo("NO_ENTITLEMENT")
    }

    @Test
    fun `check returns date and status reasons`() {
        val caller = activeCaller()
        whenever(serviceOfferingService.getEntity("gpt")).thenReturn(offering("gpt"))
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("a@x.com")).thenReturn(caller)

        whenever(entitlementRepository.findByParticipantId("p1")).thenReturn(
            listOf(entitlement(status = EntitlementStatus.PENDING))
        )
        assertThat(
            entitlementService.checkByCallerAndService(
                "a@x.com",
                "gpt",
                fromDate = LocalDate.of(2025, 6, 1)
            ).reason
        ).isEqualTo("ENTITLEMENT_NOT_ACTIVE")

        whenever(entitlementRepository.findByParticipantId("p1")).thenReturn(
            listOf(
                entitlement(
                    status = EntitlementStatus.ACTIVE,
                    validFrom = LocalDate.of(2025, 1, 1),
                    validTo = LocalDate.of(2025, 12, 31)
                )
            )
        )
        assertThat(
            entitlementService.checkByCallerAndService(
                "a@x.com",
                "gpt",
                fromDate = LocalDate.of(2024, 6, 1)
            ).reason
        ).isEqualTo("ENTITLEMENT_NOT_YET_VALID")
        assertThat(
            entitlementService.checkByCallerAndService(
                "a@x.com",
                "gpt",
                fromDate = LocalDate.of(2026, 1, 1)
            ).reason
        ).isEqualTo("ENTITLEMENT_EXPIRED")
        assertThat(
            entitlementService.checkByCallerAndService(
                "a@x.com",
                "gpt",
                fromDate = LocalDate.of(2025, 6, 1)
            ).reason
        ).isEqualTo("ALLOWED")
    }

    @Test
    fun `check requires full coverage of fromDate untilDate range`() {
        val caller = activeCaller()
        whenever(serviceOfferingService.getEntity("gpt")).thenReturn(offering("gpt"))
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("a@x.com")).thenReturn(caller)
        whenever(entitlementRepository.findByParticipantId("p1")).thenReturn(
            listOf(
                entitlement(
                    status = EntitlementStatus.ACTIVE,
                    validFrom = LocalDate.of(2025, 1, 1),
                    validTo = LocalDate.of(2025, 6, 30)
                )
            )
        )

        // Window fully inside entitlement → allowed
        val allowed = entitlementService.checkByCallerAndService(
            "a@x.com",
            "gpt",
            fromDate = LocalDate.of(2025, 2, 1),
            untilDate = LocalDate.of(2025, 6, 30)
        )
        assertThat(allowed.allowed).isTrue()
        assertThat(allowed.reason).isEqualTo("ALLOWED")
        assertThat(allowed.fromDate).isEqualTo(LocalDate.of(2025, 2, 1))
        assertThat(allowed.untilDate).isEqualTo(LocalDate.of(2025, 6, 30))

        // Window starts before validFrom → not yet valid
        assertThat(
            entitlementService.checkByCallerAndService(
                "a@x.com",
                "gpt",
                fromDate = LocalDate.of(2024, 12, 1),
                untilDate = LocalDate.of(2025, 3, 1)
            ).reason
        ).isEqualTo("ENTITLEMENT_NOT_YET_VALID")

        // Window ends after validTo → expired (does not fully cover)
        assertThat(
            entitlementService.checkByCallerAndService(
                "a@x.com",
                "gpt",
                fromDate = LocalDate.of(2025, 6, 1),
                untilDate = LocalDate.of(2025, 7, 15)
            ).reason
        ).isEqualTo("ENTITLEMENT_EXPIRED")
    }

    private fun activeCaller() = ParticipantCallerRegistration(
        callerId = "a@x.com",
        participant = participant("p1"),
        status = CallerRegistrationStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)

    @Test
    fun `create validates date range`() {
        assertThatThrownBy {
            entitlementService.create(
                CreateEntitlementRequest(
                    participantId = "p1",
                    serviceOfferingId = "gpt",
                    validFrom = LocalDate.of(2025, 6, 1),
                    validTo = LocalDate.of(2025, 1, 1)
                )
            )
        }.isInstanceOf(BadRequestException::class.java)
        verify(entitlementRepository, never()).save(any())
    }

    @Test
    fun `create throws ConflictException when already exists`() {
        whenever(participantService.getEntity("p1")).thenReturn(participant("p1"))
        whenever(serviceOfferingService.getEntity("gpt")).thenReturn(offering("gpt"))
        whenever(entitlementRepository.existsByParticipantIdAndServiceOfferingId("p1", "gpt")).thenReturn(true)

        assertThatThrownBy {
            entitlementService.create(
                CreateEntitlementRequest(participantId = "p1", serviceOfferingId = "gpt", validFrom = LocalDate.of(2025, 1, 1))
            )
        }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `create saves and returns loaded entitlement`() {
        val savedId = UUID.randomUUID()
        whenever(participantService.getEntity("p1")).thenReturn(participant("p1"))
        whenever(serviceOfferingService.getEntity("gpt")).thenReturn(offering("gpt"))
        whenever(entitlementRepository.existsByParticipantIdAndServiceOfferingId("p1", "gpt")).thenReturn(false)
        whenever(entitlementRepository.save(any(ParticipantServiceEntitlement::class.java))).thenAnswer { inv ->
            inv.getArgument<ParticipantServiceEntitlement>(0).also { it.id = savedId }
        }
        whenever(entitlementRepository.findByIdWithRelations(savedId)).thenReturn(entitlement(id = savedId))

        val result = entitlementService.create(
            CreateEntitlementRequest(
                participantId = "p1",
                serviceOfferingId = "gpt",
                status = EntitlementStatus.ACTIVE,
                validFrom = LocalDate.of(2025, 1, 1),
                config = """{"max_tpm":10}"""
            )
        )
        assertThat(result.id).isEqualTo(savedId)
        assertThat(result.config).contains("max_tpm")
    }

    @Test
    fun `update changes fields`() {
        val id = UUID.randomUUID()
        val existing = entitlement(id = id, status = EntitlementStatus.PENDING)
        whenever(entitlementRepository.findByIdWithRelations(id)).thenReturn(existing)
        whenever(entitlementRepository.save(any(ParticipantServiceEntitlement::class.java))).thenAnswer { it.getArgument(0) }

        val result = entitlementService.update(
            id,
            UpdateEntitlementRequest(
                status = EntitlementStatus.ACTIVE,
                validFrom = LocalDate.of(2025, 2, 1),
                validTo = null,
                config = """{"max_rpm":5}""",
                notes = "ok"
            )
        )
        assertThat(result.status).isEqualTo(EntitlementStatus.ACTIVE)
        assertThat(result.config).contains("max_rpm")
    }

    @Test
    fun `findById throws when missing`() {
        val id = UUID.randomUUID()
        whenever(entitlementRepository.findByIdWithRelations(id)).thenReturn(null)
        assertThatThrownBy { entitlementService.findById(id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    private fun participant(id: String) =
        Participant(id = id, name = "P", status = ParticipantStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)

    private fun offering(id: String) =
        ServiceOffering(id = id, name = "O", category = "LLM", active = true,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)

    private fun entitlement(
        id: UUID = UUID.randomUUID(),
        status: EntitlementStatus = EntitlementStatus.ACTIVE,
        validFrom: LocalDate = LocalDate.of(2025, 1, 1),
        validTo: LocalDate? = null
    ) = ParticipantServiceEntitlement(
        id = id,
        participant = participant("p1"),
        serviceOffering = offering("gpt"),
        status = status,
        validFrom = validFrom,
        validTo = validTo,
        config = """{"max_tpm":10}""",
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
}
