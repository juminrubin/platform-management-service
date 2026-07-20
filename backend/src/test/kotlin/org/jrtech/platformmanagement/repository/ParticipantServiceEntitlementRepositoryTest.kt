package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
class ParticipantServiceEntitlementRepositoryTest @Autowired constructor(
    private val entitlementRepository: ParticipantServiceEntitlementRepository,
    private val participantRepository: ParticipantRepository,
    private val serviceOfferingRepository: ServiceOfferingRepository
) {

    private lateinit var participant: Participant
    private lateinit var offering: ServiceOffering

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().take(8)
        participant = participantRepository.save(
            Participant(id = "p-$suffix", name = "Ent P $suffix", status = ParticipantStatus.ACTIVE)
        )
        offering = serviceOfferingRepository.save(
            ServiceOffering(id = "so-$suffix", name = "Offering $suffix", category = "LLM", active = true)
        )
    }

    @Test
    fun `save and findByIdWithRelations loads associations`() {
        val entitlement = entitlementRepository.save(
            ParticipantServiceEntitlement(
                participant = participant,
                serviceOffering = offering,
                status = EntitlementStatus.ACTIVE,
                validFrom = LocalDate.of(2025, 1, 1),
                validTo = LocalDate.of(2025, 12, 31),
                config = """{"max_tpm":100}""",
                notes = "repo test"
            )
        )

        val found = entitlementRepository.findByIdWithRelations(entitlement.id)
        assertThat(found).isNotNull
        assertThat(found!!.participant.id).isEqualTo(participant.id)
        assertThat(found.serviceOffering.id).isEqualTo(offering.id)
        assertThat(found.config).contains("max_tpm")
    }

    @Test
    fun `existsByParticipantIdAndServiceOfferingId`() {
        entitlementRepository.save(
            ParticipantServiceEntitlement(
                participant = participant,
                serviceOffering = offering,
                status = EntitlementStatus.PENDING,
                validFrom = LocalDate.of(2025, 1, 1)
            )
        )
        assertThat(entitlementRepository.existsByParticipantIdAndServiceOfferingId(participant.id, offering.id)).isTrue()
    }

    @Test
    fun `findByParticipantId returns entitlements`() {
        entitlementRepository.save(
            ParticipantServiceEntitlement(
                participant = participant,
                serviceOffering = offering,
                status = EntitlementStatus.ACTIVE,
                validFrom = LocalDate.of(2025, 1, 1)
            )
        )
        val result = entitlementRepository.findByParticipantId(participant.id)
        assertThat(result).isNotEmpty
        assertThat(result).allMatch { it.participant.id == participant.id }
    }
}
