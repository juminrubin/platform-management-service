package com.example.platformmanagement.service

import com.example.platformmanagement.domain.EntitlementStatus
import com.example.platformmanagement.domain.Participant
import com.example.platformmanagement.domain.ParticipantStatus
import com.example.platformmanagement.domain.ServiceOffering
import com.example.platformmanagement.dto.CreateEntitlementRequest
import com.example.platformmanagement.dto.UpdateEntitlementRequest
import com.example.platformmanagement.exception.BadRequestException
import com.example.platformmanagement.exception.ConflictException
import com.example.platformmanagement.repository.ParticipantRepository
import com.example.platformmanagement.repository.ParticipantServiceEntitlementRepository
import com.example.platformmanagement.repository.ServiceOfferingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.UUID

@DataJpaTest
@Import(EntitlementService::class, ParticipantService::class, ServiceOfferingService::class)
@ActiveProfiles("test")
class EntitlementServicePersistenceTest @Autowired constructor(
    private val entitlementService: EntitlementService,
    private val participantRepository: ParticipantRepository,
    private val serviceOfferingRepository: ServiceOfferingRepository,
    private val entitlementRepository: ParticipantServiceEntitlementRepository
) {

    private lateinit var participant: Participant
    private lateinit var offering: ServiceOffering

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().take(8)
        participant = participantRepository.save(
            Participant(id = "p-$suffix", name = "EPS $suffix", status = ParticipantStatus.ACTIVE)
        )
        offering = serviceOfferingRepository.save(
            ServiceOffering(id = "so-$suffix", name = "Offering $suffix", category = "LLM", active = true)
        )
    }

    @Test
    fun `create find update and delete entitlement end-to-end`() {
        val created = entitlementService.create(
            CreateEntitlementRequest(
                participantId = participant.id,
                serviceOfferingId = offering.id,
                status = EntitlementStatus.PENDING,
                validFrom = LocalDate.of(2025, 1, 1),
                validTo = LocalDate.of(2025, 12, 31),
                config = """{"max_tpm":25}""",
                notes = "e2e"
            )
        )
        assertThat(created.participantId).isEqualTo(participant.id)
        assertThat(created.serviceOfferingId).isEqualTo(offering.id)
        assertThat(created.config).contains("max_tpm")

        val updated = entitlementService.update(
            created.id,
            UpdateEntitlementRequest(
                status = EntitlementStatus.ACTIVE,
                validFrom = LocalDate.of(2025, 1, 1),
                validTo = null,
                config = """{"max_rpm":10}""",
                notes = "activated"
            )
        )
        assertThat(updated.status).isEqualTo(EntitlementStatus.ACTIVE)
        assertThat(updated.config).contains("max_rpm")

        entitlementService.delete(created.id)
        assertThat(entitlementRepository.existsById(created.id)).isFalse()
    }

    @Test
    fun `create rejects duplicate participant-offering pair`() {
        entitlementService.create(
            CreateEntitlementRequest(participantId = participant.id, serviceOfferingId = offering.id, validFrom = LocalDate.of(2025, 1, 1))
        )
        assertThatThrownBy {
            entitlementService.create(
                CreateEntitlementRequest(participantId = participant.id, serviceOfferingId = offering.id, validFrom = LocalDate.of(2025, 2, 1))
            )
        }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `create rejects invalid date range`() {
        assertThatThrownBy {
            entitlementService.create(
                CreateEntitlementRequest(
                    participantId = participant.id,
                    serviceOfferingId = offering.id,
                    validFrom = LocalDate.of(2025, 12, 1),
                    validTo = LocalDate.of(2025, 1, 1)
                )
            )
        }.isInstanceOf(BadRequestException::class.java)
    }
}
