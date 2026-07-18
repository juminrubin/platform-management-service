package com.example.platformmanagement.service

import com.example.platformmanagement.domain.EntitlementStatus
import com.example.platformmanagement.domain.Participant
import com.example.platformmanagement.domain.ParticipantServiceEntitlement
import com.example.platformmanagement.domain.ParticipantStatus
import com.example.platformmanagement.domain.ServiceOffering
import com.example.platformmanagement.dto.CreateEntitlementRequest
import com.example.platformmanagement.dto.UpdateEntitlementRequest
import com.example.platformmanagement.exception.BadRequestException
import com.example.platformmanagement.exception.ConflictException
import com.example.platformmanagement.exception.ResourceNotFoundException
import com.example.platformmanagement.repository.ParticipantCallerIdentityRepository
import com.example.platformmanagement.repository.ParticipantServiceEntitlementRepository
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

@ExtendWith(MockitoExtension::class)
class EntitlementServiceTest {

    @Mock
    private lateinit var entitlementRepository: ParticipantServiceEntitlementRepository

    @Mock
    private lateinit var callerIdentityRepository: ParticipantCallerIdentityRepository

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
        Participant(id = id, name = "P", status = ParticipantStatus.ACTIVE)

    private fun offering(id: String) =
        ServiceOffering(id = id, name = "O", category = "LLM", active = true)

    private fun entitlement(
        id: UUID = UUID.randomUUID(),
        status: EntitlementStatus = EntitlementStatus.ACTIVE
    ) = ParticipantServiceEntitlement(
        id = id,
        participant = participant("p1"),
        serviceOffering = offering("gpt"),
        status = status,
        validFrom = LocalDate.of(2025, 1, 1),
        config = """{"max_tpm":10}"""
    )
}
