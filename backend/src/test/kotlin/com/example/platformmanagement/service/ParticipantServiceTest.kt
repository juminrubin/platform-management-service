package com.example.platformmanagement.service

import com.example.platformmanagement.domain.Participant
import com.example.platformmanagement.domain.ParticipantStatus
import com.example.platformmanagement.dto.CreateParticipantRequest
import com.example.platformmanagement.dto.UpdateParticipantRequest
import com.example.platformmanagement.exception.ConflictException
import com.example.platformmanagement.exception.ResourceNotFoundException
import com.example.platformmanagement.repository.ParticipantRepository
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
class ParticipantServiceTest {

    @Mock
    private lateinit var participantRepository: ParticipantRepository

    @InjectMocks
    private lateinit var participantService: ParticipantService

    @Test
    fun `findAll without status returns all`() {
        whenever(participantRepository.findAll()).thenReturn(
            listOf(participant("a", "A"), participant("b", "B"))
        )
        assertThat(participantService.findAll(null)).hasSize(2)
    }

    @Test
    fun `create persists trimmed fields`() {
        whenever(participantRepository.existsById("acme")).thenReturn(false)
        whenever(participantRepository.existsByName("Acme")).thenReturn(false)
        whenever(participantRepository.save(any(Participant::class.java))).thenAnswer { it.getArgument(0) }

        val result = participantService.create(
            CreateParticipantRequest(id = " acme ", name = " Acme ", contact = " ops@x.com ", status = ParticipantStatus.ACTIVE)
        )
        assertThat(result.id).isEqualTo("acme")
        assertThat(result.name).isEqualTo("Acme")
        assertThat(result.contact).isEqualTo("ops@x.com")
    }

    @Test
    fun `create throws when id exists`() {
        whenever(participantRepository.existsById("dup")).thenReturn(true)
        assertThatThrownBy {
            participantService.create(CreateParticipantRequest(id = "dup", name = "X"))
        }.isInstanceOf(ConflictException::class.java)
        verify(participantRepository, never()).save(any())
    }

    @Test
    fun `create throws when name exists`() {
        whenever(participantRepository.existsById("new")).thenReturn(false)
        whenever(participantRepository.existsByName("Taken")).thenReturn(true)
        assertThatThrownBy {
            participantService.create(CreateParticipantRequest(id = "new", name = "Taken"))
        }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `update throws when name taken by another`() {
        whenever(participantRepository.findById("p1")).thenReturn(Optional.of(participant("p1", "Old")))
        whenever(participantRepository.existsByNameAndIdNot("Other", "p1")).thenReturn(true)
        assertThatThrownBy {
            participantService.update("p1", UpdateParticipantRequest(name = "Other", contact = null, status = ParticipantStatus.ACTIVE))
        }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `delete throws when missing`() {
        whenever(participantRepository.existsById("missing")).thenReturn(false)
        assertThatThrownBy { participantService.delete("missing") }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `findById throws when missing`() {
        whenever(participantRepository.findById("missing")).thenReturn(Optional.empty())
        assertThatThrownBy { participantService.findById("missing") }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    private fun participant(id: String, name: String) =
        Participant(id = id, name = name, contact = null, status = ParticipantStatus.ACTIVE)
}
