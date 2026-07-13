package com.example.participantapi.service

import com.example.participantapi.domain.CallerIdentityStatus
import com.example.participantapi.domain.Participant
import com.example.participantapi.domain.ParticipantStatus
import com.example.participantapi.domain.ServiceOffering
import com.example.participantapi.dto.CreateCallerIdentityRequest
import com.example.participantapi.dto.CreateConsumptionRequest
import com.example.participantapi.dto.UpdateCallerIdentityRequest
import com.example.participantapi.repository.ParticipantCallConsumptionRepository
import com.example.participantapi.repository.ParticipantCallerIdentityRepository
import com.example.participantapi.repository.ParticipantRepository
import com.example.participantapi.repository.ServiceOfferingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest
@Import(
    CallerIdentityService::class,
    ConsumptionService::class,
    ParticipantService::class,
    ServiceOfferingService::class
)
@ActiveProfiles("test")
class CallerIdentityAndConsumptionPersistenceTest @Autowired constructor(
    private val callerIdentityService: CallerIdentityService,
    private val consumptionService: ConsumptionService,
    private val participantRepository: ParticipantRepository,
    private val serviceOfferingRepository: ServiceOfferingRepository,
    private val callerIdentityRepository: ParticipantCallerIdentityRepository,
    private val consumptionRepository: ParticipantCallConsumptionRepository
) {

    private lateinit var participant: Participant
    private lateinit var offering: ServiceOffering

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().take(8)
        participant = participantRepository.save(
            Participant(id = "p-$suffix", name = "CC $suffix", status = ParticipantStatus.ACTIVE)
        )
        offering = serviceOfferingRepository.save(
            ServiceOffering(id = "so-$suffix", name = "Model $suffix", category = "LLM", active = true)
        )
    }

    @Test
    fun `caller identity and consumption end-to-end`() {
        val callerIdentity = callerIdentityService.create(
            CreateCallerIdentityRequest(
                participantId = participant.id,
                callerIdentity = "user@example.com",
                status = CallerIdentityStatus.ACTIVE
            )
        )
        assertThat(callerIdentity.participantId).isEqualTo(participant.id)

        val updated = callerIdentityService.update(
            callerIdentity.id,
            UpdateCallerIdentityRequest(status = CallerIdentityStatus.INACTIVE)
        )
        assertThat(updated.status).isEqualTo(CallerIdentityStatus.INACTIVE)

        callerIdentityService.update(
            callerIdentity.id,
            UpdateCallerIdentityRequest(status = CallerIdentityStatus.ACTIVE)
        )

        val consumption = consumptionService.create(
            CreateConsumptionRequest(
                participantCallerIdentityId = callerIdentity.id,
                serviceOfferingId = offering.id,
                consumptionData = """{"input_token":100,"output_token":20}"""
            )
        )
        assertThat(consumption.callerIdentity).isEqualTo("user@example.com")
        assertThat(consumption.serviceOfferingId).isEqualTo(offering.id)
        assertThat(consumptionRepository.existsById(consumption.id)).isTrue()

        consumptionService.delete(consumption.id)
        callerIdentityService.delete(callerIdentity.id)
        assertThat(callerIdentityRepository.existsById(callerIdentity.id)).isFalse()
    }
}
