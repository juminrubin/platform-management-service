package com.example.platformmanagement.repository

import com.example.platformmanagement.domain.CallerIdentityStatus
import com.example.platformmanagement.domain.Participant
import com.example.platformmanagement.domain.ParticipantCallConsumption
import com.example.platformmanagement.domain.ParticipantCallerIdentity
import com.example.platformmanagement.domain.ParticipantStatus
import com.example.platformmanagement.domain.ServiceOffering
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
class ParticipantCallConsumptionRepositoryTest @Autowired constructor(
    private val consumptionRepository: ParticipantCallConsumptionRepository,
    private val callerIdentityRepository: ParticipantCallerIdentityRepository,
    private val participantRepository: ParticipantRepository,
    private val serviceOfferingRepository: ServiceOfferingRepository
) {

    private lateinit var callerIdentity: ParticipantCallerIdentity
    private lateinit var offering: ServiceOffering

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().take(8)
        val participant = participantRepository.save(
            Participant(id = "p-$suffix", name = "Cons P $suffix", status = ParticipantStatus.ACTIVE)
        )
        callerIdentity = callerIdentityRepository.save(
            ParticipantCallerIdentity(
                participant = participant,
                callerIdentity = "caller-$suffix",
                status = CallerIdentityStatus.ACTIVE
            )
        )
        offering = serviceOfferingRepository.save(
            ServiceOffering(id = "so-$suffix", name = "Model $suffix", category = "LLM", active = true)
        )
    }

    @Test
    fun `save and findByIdWithRelations`() {
        val saved = consumptionRepository.save(
            ParticipantCallConsumption(
                participantCallerIdentity = callerIdentity,
                serviceOffering = offering,
                consumptionData = """{"input_token":10,"output_token":5}"""
            )
        )
        val found = consumptionRepository.findByIdWithRelations(saved.id)
        assertThat(found).isNotNull
        assertThat(found!!.participantCallerIdentity.id).isEqualTo(callerIdentity.id)
        assertThat(found.serviceOffering.id).isEqualTo(offering.id)
        assertThat(found.consumptionData).contains("input_token")
    }

    @Test
    fun `findByParticipantCallerIdentityId`() {
        consumptionRepository.save(
            ParticipantCallConsumption(
                participantCallerIdentity = callerIdentity,
                serviceOffering = offering,
                consumptionData = "{}"
            )
        )
        val result = consumptionRepository.findByParticipantCallerIdentityId(callerIdentity.id)
        assertThat(result).isNotEmpty
        assertThat(result).allMatch { it.participantCallerIdentity.id == callerIdentity.id }
    }
}
