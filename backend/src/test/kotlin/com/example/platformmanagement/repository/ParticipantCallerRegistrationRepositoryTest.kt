package com.example.platformmanagement.repository

import com.example.platformmanagement.domain.CallerRegistrationStatus
import com.example.platformmanagement.domain.Participant
import com.example.platformmanagement.domain.ParticipantCallerRegistration
import com.example.platformmanagement.domain.ParticipantStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
class ParticipantCallerRegistrationRepositoryTest @Autowired constructor(
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val participantRepository: ParticipantRepository
) {

    private lateinit var participant: Participant

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().take(8)
        participant = participantRepository.save(
            Participant(id = "p-$suffix", name = "Caller P $suffix", status = ParticipantStatus.ACTIVE)
        )
    }

    @Test
    fun `save and findByCallerIdWithParticipant`() {
        val saved = callerRegistrationRepository.save(
            ParticipantCallerRegistration(
                callerId = "user@example.com",
                participant = participant,
                status = CallerRegistrationStatus.ACTIVE
            )
        )
        val found = callerRegistrationRepository.findByCallerIdWithParticipant(saved.callerId)
        assertThat(found).isNotNull
        assertThat(found!!.callerId).isEqualTo("user@example.com")
        assertThat(found.participant.name).isEqualTo(participant.name)
    }

    @Test
    fun `existsByCallerId`() {
        callerRegistrationRepository.save(
            ParticipantCallerRegistration(
                callerId = "sp-client-id",
                participant = participant,
                status = CallerRegistrationStatus.ACTIVE
            )
        )
        assertThat(callerRegistrationRepository.existsByCallerId("sp-client-id")).isTrue()
        assertThat(callerRegistrationRepository.existsByCallerId("missing")).isFalse()
    }
}
