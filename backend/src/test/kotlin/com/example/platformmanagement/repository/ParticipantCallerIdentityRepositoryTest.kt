package com.example.platformmanagement.repository

import com.example.platformmanagement.domain.CallerIdentityStatus
import com.example.platformmanagement.domain.Participant
import com.example.platformmanagement.domain.ParticipantCallerIdentity
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
class ParticipantCallerIdentityRepositoryTest @Autowired constructor(
    private val callerIdentityRepository: ParticipantCallerIdentityRepository,
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
    fun `save and findByIdWithParticipant`() {
        val saved = callerIdentityRepository.save(
            ParticipantCallerIdentity(
                participant = participant,
                callerIdentity = "user@example.com",
                status = CallerIdentityStatus.ACTIVE
            )
        )
        val found = callerIdentityRepository.findByIdWithParticipant(saved.id)
        assertThat(found).isNotNull
        assertThat(found!!.callerIdentity).isEqualTo("user@example.com")
        assertThat(found.participant.name).isEqualTo(participant.name)
    }

    @Test
    fun `existsByParticipantIdAndCallerIdentity`() {
        callerIdentityRepository.save(
            ParticipantCallerIdentity(
                participant = participant,
                callerIdentity = "sp-client-id",
                status = CallerIdentityStatus.ACTIVE
            )
        )
        assertThat(
            callerIdentityRepository.existsByParticipantIdAndCallerIdentity(participant.id, "sp-client-id")
        ).isTrue()
        assertThat(
            callerIdentityRepository.existsByParticipantIdAndCallerIdentity(participant.id, "missing")
        ).isFalse()
    }
}
