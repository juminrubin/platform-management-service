package com.example.participantapi.service

import com.example.participantapi.domain.CallerIdentityStatus
import com.example.participantapi.domain.Participant
import com.example.participantapi.domain.ParticipantCallerIdentity
import com.example.participantapi.domain.ParticipantStatus
import com.example.participantapi.dto.CreateCallerIdentityRequest
import com.example.participantapi.dto.UpdateCallerIdentityRequest
import com.example.participantapi.exception.ConflictException
import com.example.participantapi.exception.ResourceNotFoundException
import com.example.participantapi.repository.ParticipantCallerIdentityRepository
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
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CallerIdentityServiceTest {

    @Mock
    private lateinit var callerIdentityRepository: ParticipantCallerIdentityRepository

    @Mock
    private lateinit var participantService: ParticipantService

    @InjectMocks
    private lateinit var callerIdentityService: CallerIdentityService

    @Test
    fun `create succeeds`() {
        val savedId = UUID.randomUUID()
        val participant = Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE)
        whenever(participantService.getEntity("acme")).thenReturn(participant)
        whenever(callerIdentityRepository.existsByParticipantIdAndCallerIdentity("acme", "user@x.com"))
            .thenReturn(false)
        whenever(callerIdentityRepository.save(any(ParticipantCallerIdentity::class.java))).thenAnswer { inv ->
            inv.getArgument<ParticipantCallerIdentity>(0).also { it.id = savedId }
        }
        whenever(callerIdentityRepository.findByIdWithParticipant(savedId)).thenReturn(
            ParticipantCallerIdentity(
                id = savedId,
                participant = participant,
                callerIdentity = "user@x.com",
                status = CallerIdentityStatus.ACTIVE
            )
        )

        val result = callerIdentityService.create(
            CreateCallerIdentityRequest(participantId = "acme", callerIdentity = " user@x.com ")
        )
        assertThat(result.callerIdentity).isEqualTo("user@x.com")
        assertThat(result.participantId).isEqualTo("acme")
    }

    @Test
    fun `create throws on duplicate`() {
        whenever(participantService.getEntity("acme")).thenReturn(
            Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE)
        )
        whenever(callerIdentityRepository.existsByParticipantIdAndCallerIdentity("acme", "dup")).thenReturn(true)

        assertThatThrownBy {
            callerIdentityService.create(
                CreateCallerIdentityRequest(participantId = "acme", callerIdentity = "dup")
            )
        }.isInstanceOf(ConflictException::class.java)
        verify(callerIdentityRepository, never()).save(any())
    }

    @Test
    fun `update status`() {
        val id = UUID.randomUUID()
        val entity = ParticipantCallerIdentity(
            id = id,
            participant = Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE),
            callerIdentity = "c1",
            status = CallerIdentityStatus.ACTIVE
        )
        whenever(callerIdentityRepository.findByIdWithParticipant(id)).thenReturn(entity)
        whenever(callerIdentityRepository.save(any(ParticipantCallerIdentity::class.java)))
            .thenAnswer { it.getArgument(0) }

        val result = callerIdentityService.update(
            id,
            UpdateCallerIdentityRequest(status = CallerIdentityStatus.REVOKED)
        )
        assertThat(result.status).isEqualTo(CallerIdentityStatus.REVOKED)
    }

    @Test
    fun `delete throws when missing`() {
        val id = UUID.randomUUID()
        whenever(callerIdentityRepository.existsById(id)).thenReturn(false)
        assertThatThrownBy { callerIdentityService.delete(id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
