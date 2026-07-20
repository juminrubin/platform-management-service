package com.example.platformmanagement.service

import com.example.platformmanagement.domain.CallerRegistrationStatus
import com.example.platformmanagement.domain.Participant
import com.example.platformmanagement.domain.ParticipantCallConsumption
import com.example.platformmanagement.domain.ParticipantCallerRegistration
import com.example.platformmanagement.domain.ParticipantStatus
import com.example.platformmanagement.domain.ServiceOffering
import com.example.platformmanagement.dto.CreateConsumptionRequest
import com.example.platformmanagement.exception.ResourceNotFoundException
import com.example.platformmanagement.repository.ParticipantCallConsumptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ConsumptionServiceTest {

    @Mock
    private lateinit var consumptionRepository: ParticipantCallConsumptionRepository

    @Mock
    private lateinit var callerRegistrationService: CallerRegistrationService

    @Mock
    private lateinit var serviceOfferingService: ServiceOfferingService

    @InjectMocks
    private lateinit var consumptionService: ConsumptionService

    @Test
    fun `findAll by caller id`() {
        whenever(consumptionRepository.findByCallerId("u@x.com")).thenReturn(emptyList())
        assertThat(consumptionService.findAll("u@x.com", null)).isEmpty()
        verify(consumptionRepository).findByCallerId("u@x.com")
    }

    @Test
    fun `findAll by service offering id`() {
        whenever(consumptionRepository.findByServiceOfferingId("gpt-5.1")).thenReturn(emptyList())
        assertThat(consumptionService.findAll(null, "gpt-5.1")).isEmpty()
        verify(consumptionRepository).findByServiceOfferingId("gpt-5.1")
    }

    @Test
    fun `findAll without filters uses findAllWithRelations`() {
        whenever(consumptionRepository.findAllWithRelations()).thenReturn(emptyList())
        assertThat(consumptionService.findAll(null, null)).isEmpty()
        verify(consumptionRepository).findAllWithRelations()
    }

    @Test
    fun `delete throws when missing`() {
        val id = UUID.randomUUID()
        whenever(consumptionRepository.existsById(id)).thenReturn(false)
        assertThatThrownBy { consumptionService.delete(id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `create records consumption`() {
        val savedId = UUID.randomUUID()
        val participant = Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE)
        val callerRegistration = ParticipantCallerRegistration(
            callerId = "u@x.com",
            participant = participant,
            status = CallerRegistrationStatus.ACTIVE
        )
        val offering = ServiceOffering(id = "gpt-5.1", name = "GPT", category = "LLM", active = true)

        whenever(callerRegistrationService.getEntity("u@x.com")).thenReturn(callerRegistration)
        whenever(serviceOfferingService.getEntity("gpt-5.1")).thenReturn(offering)
        whenever(consumptionRepository.save(any(ParticipantCallConsumption::class.java))).thenAnswer { inv ->
            inv.getArgument<ParticipantCallConsumption>(0).also { it.id = savedId }
        }
        whenever(consumptionRepository.findByIdWithRelations(savedId)).thenReturn(
            ParticipantCallConsumption(
                id = savedId,
                callerRegistration = callerRegistration,
                serviceOffering = offering,
                consumptionData = """{"input_token":1}"""
            )
        )

        val result = consumptionService.create(
            CreateConsumptionRequest(
                callerId = "u@x.com",
                serviceOfferingId = "gpt-5.1",
                consumptionData = """{"input_token":1}"""
            )
        )
        assertThat(result.id).isEqualTo(savedId)
        assertThat(result.serviceOfferingId).isEqualTo("gpt-5.1")
        assertThat(result.callerId).isEqualTo("u@x.com")
    }

    @Test
    fun `findById throws when missing`() {
        val id = UUID.randomUUID()
        whenever(consumptionRepository.findByIdWithRelations(id)).thenReturn(null)
        assertThatThrownBy { consumptionService.findById(id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `delete removes when present`() {
        val id = UUID.randomUUID()
        whenever(consumptionRepository.existsById(id)).thenReturn(true)
        consumptionService.delete(id)
        verify(consumptionRepository).deleteById(id)
    }
}
