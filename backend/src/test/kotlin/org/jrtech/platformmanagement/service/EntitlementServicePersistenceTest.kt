package org.jrtech.platformmanagement.service

import org.springframework.boot.test.context.SpringBootTest

import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.dto.CreateEntitlementRequest
import org.jrtech.platformmanagement.dto.UpdateEntitlementRequest
import org.jrtech.platformmanagement.exception.BadRequestException
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.UUID
import org.jrtech.platformmanagement.TestAudit

@SpringBootTest
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
            Participant(id = "p-$suffix", name = "EPS $suffix", status = ParticipantStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        )
        offering = serviceOfferingRepository.save(
            ServiceOffering(id = "so-$suffix", name = "Offering $suffix", category = "LLM", active = true,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
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
