package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.dto.CreateCallerRegistrationRequest
import org.jrtech.platformmanagement.dto.UpdateCallerRegistrationRequest
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.exception.ResourceNotFoundException
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
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
import org.jrtech.platformmanagement.TestAudit

@ExtendWith(MockitoExtension::class)
class CallerRegistrationServiceTest {

    @Mock
    private lateinit var callerRegistrationRepository: ParticipantCallerRegistrationRepository

    @Mock
    private lateinit var participantService: ParticipantService

    @InjectMocks
    private lateinit var callerRegistrationService: CallerRegistrationService

    @Test
    fun `findAll by participant and status`() {
        val entity = ParticipantCallerRegistration(
            callerId = "a@x.com",
            participant = Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY),
            status = CallerRegistrationStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        whenever(callerRegistrationRepository.findByParticipantId("acme")).thenReturn(listOf(entity))
        val result = callerRegistrationService.findAll("acme", CallerRegistrationStatus.ACTIVE)
        assertThat(result).hasSize(1)
        assertThat(result[0].callerId).isEqualTo("a@x.com")
    }

    @Test
    fun `findAll without participant loads all and filters status`() {
        val participant = Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        val active = ParticipantCallerRegistration(
            callerId = "a@x.com",
            participant = participant,
            status = CallerRegistrationStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        val inactive = ParticipantCallerRegistration(
            callerId = "b@x.com",
            participant = participant,
            status = CallerRegistrationStatus.INACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        whenever(callerRegistrationRepository.findAllWithParticipant()).thenReturn(listOf(active, inactive))
        assertThat(callerRegistrationService.findAll(null, CallerRegistrationStatus.INACTIVE)).hasSize(1)
    }

    @Test
    fun `findByCallerId throws when missing`() {
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("missing")).thenReturn(null)
        assertThatThrownBy { callerRegistrationService.findByCallerId("missing") }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `update throws when missing`() {
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("missing")).thenReturn(null)
        assertThatThrownBy {
            callerRegistrationService.update(
                "missing",
                UpdateCallerRegistrationRequest(status = CallerRegistrationStatus.REVOKED)
            )
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `create succeeds`() {
        val participant = Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        whenever(participantService.getEntity("acme")).thenReturn(participant)
        whenever(callerRegistrationRepository.existsByCallerId("user@x.com")).thenReturn(false)
        whenever(callerRegistrationRepository.save(org.mockito.kotlin.anyOrNull())).thenAnswer { inv ->
            inv.getArgument(0)
        }
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("user@x.com")).thenReturn(
            ParticipantCallerRegistration(
                callerId = "user@x.com",
                participant = participant,
                status = CallerRegistrationStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        )

        val result = callerRegistrationService.create(
            CreateCallerRegistrationRequest(participantId = "acme", callerId = " user@x.com ")
        )
        assertThat(result.callerId).isEqualTo("user@x.com")
        assertThat(result.participantId).isEqualTo("acme")
    }

    @Test
    fun `create throws on duplicate`() {
        whenever(participantService.getEntity("acme")).thenReturn(
            Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        )
        whenever(callerRegistrationRepository.existsByCallerId("dup")).thenReturn(true)

        assertThatThrownBy {
            callerRegistrationService.create(
                CreateCallerRegistrationRequest(participantId = "acme", callerId = "dup")
            )
        }.isInstanceOf(ConflictException::class.java)
        verify(callerRegistrationRepository, never()).save(org.mockito.kotlin.anyOrNull())
    }

    @Test
    fun `update status`() {
        val entity = ParticipantCallerRegistration(
            callerId = "c1",
            participant = Participant(id = "acme", name = "Acme", status = ParticipantStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY),
            status = CallerRegistrationStatus.ACTIVE,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        whenever(callerRegistrationRepository.findByCallerIdWithParticipant("c1")).thenReturn(entity)
        whenever(callerRegistrationRepository.save(org.mockito.kotlin.anyOrNull()))
            .thenAnswer { it.getArgument(0) }

        val result = callerRegistrationService.update(
            "c1",
            UpdateCallerRegistrationRequest(status = CallerRegistrationStatus.REVOKED)
        )
        assertThat(result.status).isEqualTo(CallerRegistrationStatus.REVOKED)
    }

    @Test
    fun `delete throws when missing`() {
        whenever(callerRegistrationRepository.existsById("missing")).thenReturn(false)
        assertThatThrownBy { callerRegistrationService.delete("missing") }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
