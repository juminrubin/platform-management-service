package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.dto.CreateServiceOfferingRequest
import org.jrtech.platformmanagement.dto.UpdateServiceOfferingRequest
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
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
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ServiceOfferingServiceTest {

    @Mock
    private lateinit var serviceOfferingRepository: ServiceOfferingRepository

    @InjectMocks
    private lateinit var serviceOfferingService: ServiceOfferingService

    @Test
    fun `findAll activeOnly uses findByActiveTrue`() {
        whenever(serviceOfferingRepository.findByActiveTrue()).thenReturn(listOf(offering("gpt", active = true)))
        val result = serviceOfferingService.findAll(activeOnly = true, category = null)
        assertThat(result).hasSize(1)
        verify(serviceOfferingRepository).findByActiveTrue()
    }

    @Test
    fun `findAll filters by category case-insensitively`() {
        whenever(serviceOfferingRepository.findAll()).thenReturn(
            listOf(offering("gpt", active = true), offering("stt", active = true).also { it.category = "SPEECH" })
        )
        val result = serviceOfferingService.findAll(activeOnly = false, category = "speech")
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("stt")
    }

    @Test
    fun `findById returns offering`() {
        whenever(serviceOfferingRepository.findById("gpt")).thenReturn(Optional.of(offering("gpt")))
        assertThat(serviceOfferingService.findById("gpt").id).isEqualTo("gpt")
    }

    @Test
    fun `findById throws when missing`() {
        whenever(serviceOfferingRepository.findById("missing")).thenReturn(Optional.empty())
        assertThatThrownBy { serviceOfferingService.findById("missing") }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `delete removes existing offering`() {
        whenever(serviceOfferingRepository.existsById("gpt")).thenReturn(true)
        serviceOfferingService.delete("gpt")
        verify(serviceOfferingRepository).deleteById("gpt")
    }

    @Test
    fun `create uppercases category and keeps id`() {
        whenever(serviceOfferingRepository.existsById("gpt-5.1")).thenReturn(false)
        whenever(serviceOfferingRepository.save(any(ServiceOffering::class.java))).thenAnswer { it.getArgument(0) }

        val result = serviceOfferingService.create(
            CreateServiceOfferingRequest(
                id = "gpt-5.1",
                name = "GPT",
                category = " llm ",
                config = """{"default_max_tpm":100}""",
                active = true
            )
        )
        assertThat(result.id).isEqualTo("gpt-5.1")
        assertThat(result.category).isEqualTo("LLM")
        assertThat(result.config).contains("default_max_tpm")
    }

    @Test
    fun `create throws when id exists`() {
        whenever(serviceOfferingRepository.existsById("dup")).thenReturn(true)
        assertThatThrownBy {
            serviceOfferingService.create(CreateServiceOfferingRequest(id = "dup", name = "X", category = "LLM"))
        }.isInstanceOf(ConflictException::class.java)
        verify(serviceOfferingRepository, never()).save(any())
    }

    @Test
    fun `update modifies offering`() {
        whenever(serviceOfferingRepository.findById("gpt")).thenReturn(Optional.of(offering("gpt")))
        whenever(serviceOfferingRepository.save(any(ServiceOffering::class.java))).thenAnswer { it.getArgument(0) }

        val result = serviceOfferingService.update(
            "gpt",
            UpdateServiceOfferingRequest(name = "Updated", description = null, category = "speech", config = "{}", active = false)
        )
        assertThat(result.name).isEqualTo("Updated")
        assertThat(result.category).isEqualTo("SPEECH")
        assertThat(result.active).isFalse()
    }

    @Test
    fun `delete throws when missing`() {
        whenever(serviceOfferingRepository.existsById("missing")).thenReturn(false)
        assertThatThrownBy { serviceOfferingService.delete("missing") }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    private fun offering(id: String, active: Boolean = true) =
        ServiceOffering(id = id, name = "Name", category = "LLM", active = active)
}
