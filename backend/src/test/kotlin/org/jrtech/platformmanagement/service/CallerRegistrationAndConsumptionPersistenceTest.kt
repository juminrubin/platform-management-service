package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.dto.CreateCallerRegistrationRequest
import org.jrtech.platformmanagement.dto.CreateConsumptionRequest
import org.jrtech.platformmanagement.dto.UpdateCallerRegistrationRequest
import org.jrtech.platformmanagement.repository.ParticipantCallConsumptionRepository
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
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
    CallerRegistrationService::class,
    ConsumptionService::class,
    ParticipantService::class,
    ServiceOfferingService::class
)
@ActiveProfiles("test")
class CallerRegistrationAndConsumptionPersistenceTest @Autowired constructor(
    private val callerRegistrationService: CallerRegistrationService,
    private val consumptionService: ConsumptionService,
    private val participantRepository: ParticipantRepository,
    private val serviceOfferingRepository: ServiceOfferingRepository,
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
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
    fun `caller registration and consumption end-to-end`() {
        val registration = callerRegistrationService.create(
            CreateCallerRegistrationRequest(
                participantId = participant.id,
                callerId = "user@example.com",
                status = CallerRegistrationStatus.ACTIVE
            )
        )
        assertThat(registration.participantId).isEqualTo(participant.id)
        assertThat(registration.callerId).isEqualTo("user@example.com")

        val updated = callerRegistrationService.update(
            registration.callerId,
            UpdateCallerRegistrationRequest(status = CallerRegistrationStatus.INACTIVE)
        )
        assertThat(updated.status).isEqualTo(CallerRegistrationStatus.INACTIVE)

        callerRegistrationService.update(
            registration.callerId,
            UpdateCallerRegistrationRequest(status = CallerRegistrationStatus.ACTIVE)
        )

        val consumption = consumptionService.create(
            CreateConsumptionRequest(
                callerId = registration.callerId,
                serviceOfferingId = offering.id,
                consumptionData = """{"input_token":100,"output_token":20}"""
            )
        )
        assertThat(consumption.callerId).isEqualTo("user@example.com")
        assertThat(consumption.serviceOfferingId).isEqualTo(offering.id)
        assertThat(consumptionRepository.existsById(consumption.id)).isTrue()

        consumptionService.delete(consumption.id)
        callerRegistrationService.delete(registration.callerId)
        assertThat(callerRegistrationRepository.existsById(registration.callerId)).isFalse()
    }
}
