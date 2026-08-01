package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import org.jrtech.platformmanagement.TestAudit

@DataJpaTest
@ActiveProfiles("test")
class ParticipantCallConsumptionRepositoryTest @Autowired constructor(
    private val consumptionRepository: ParticipantCallConsumptionRepository,
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val participantRepository: ParticipantRepository,
    private val serviceOfferingRepository: ServiceOfferingRepository
) {

    private lateinit var callerRegistration: ParticipantCallerRegistration
    private lateinit var offering: ServiceOffering

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().take(8)
        val participant = participantRepository.save(
            Participant(id = "p-$suffix", name = "Cons P $suffix", status = ParticipantStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        )
        callerRegistration = callerRegistrationRepository.save(
            ParticipantCallerRegistration(
                callerId = "caller-$suffix",
                participant = participant,
                status = CallerRegistrationStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        )
        offering = serviceOfferingRepository.save(
            ServiceOffering(id = "so-$suffix", name = "Model $suffix", category = "LLM", active = true,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        )
    }

    @Test
    fun `save and findByIdWithRelations and sourceRefId`() {
        val sourceRef = "req-${UUID.randomUUID()}"
        val saved = consumptionRepository.save(
            ParticipantCallConsumption(
                callerRegistration = callerRegistration,
                serviceOffering = offering,
                sourceRefId = sourceRef,
                consumptionData = """{"input_token":10,"output_token":5}"""
            )
        )
        val found = consumptionRepository.findByIdWithRelations(saved.id)
        assertThat(found).isNotNull
        assertThat(found!!.callerRegistration.callerId).isEqualTo(callerRegistration.callerId)
        assertThat(found.serviceOffering.id).isEqualTo(offering.id)
        assertThat(found.sourceRefId).isEqualTo(sourceRef)
        assertThat(found.consumptionData).contains("input_token")
        assertThat(found.capturedAt).isNotNull
        assertThat(found.createdAt).isNotNull

        val byRef = consumptionRepository.findBySourceRefIdWithRelations(sourceRef)
        assertThat(byRef).isNotNull
        assertThat(byRef!!.id).isEqualTo(saved.id)
        assertThat(consumptionRepository.existsBySourceRefId(sourceRef)).isTrue()
    }

    @Test
    fun `findByCallerId`() {
        consumptionRepository.save(
            ParticipantCallConsumption(
                callerRegistration = callerRegistration,
                serviceOffering = offering,
                sourceRefId = "req-caller-${UUID.randomUUID()}",
                consumptionData = "{}"
            )
        )
        val result = consumptionRepository.findByCallerId(callerRegistration.callerId)
        assertThat(result).isNotEmpty
        assertThat(result).allMatch { it.callerRegistration.callerId == callerRegistration.callerId }
    }
}
