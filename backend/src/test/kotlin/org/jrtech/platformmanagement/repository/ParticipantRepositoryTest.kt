package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
class ParticipantRepositoryTest @Autowired constructor(
    private val participantRepository: ParticipantRepository
) {

    @Test
    fun `save and findById returns participant`() {
        val id = "p-${UUID.randomUUID().toString().take(8)}"
        val saved = participantRepository.save(
            Participant(id = id, name = "Test Corp", contact = "ops@test.example", status = ParticipantStatus.ACTIVE)
        )

        val found = participantRepository.findById(saved.id)
        assertThat(found).isPresent
        assertThat(found.get().name).isEqualTo("Test Corp")
        assertThat(found.get().contact).isEqualTo("ops@test.example")
    }

    @Test
    fun `findByName returns matching participant`() {
        val id = "p-${UUID.randomUUID().toString().take(8)}"
        participantRepository.save(Participant(id = id, name = "Unique Name $id", status = ParticipantStatus.ACTIVE))

        assertThat(participantRepository.findByName("Unique Name $id")).isNotNull
        assertThat(participantRepository.findByName("missing")).isNull()
    }

    @Test
    fun `findByStatus returns only matching status`() {
        val suffix = UUID.randomUUID().toString().take(8)
        participantRepository.save(Participant(id = "act-$suffix", name = "Active $suffix", status = ParticipantStatus.ACTIVE))
        participantRepository.save(Participant(id = "sus-$suffix", name = "Suspended $suffix", status = ParticipantStatus.SUSPENDED))

        val suspended = participantRepository.findByStatus(ParticipantStatus.SUSPENDED)
        assertThat(suspended.map { it.id }).contains("sus-$suffix")
        assertThat(suspended.map { it.id }).doesNotContain("act-$suffix")
    }

    @Test
    fun `existsByName and existsByNameAndIdNot`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val id = "ex-$suffix"
        participantRepository.save(Participant(id = id, name = "Exists $suffix", status = ParticipantStatus.INACTIVE))

        assertThat(participantRepository.existsByName("Exists $suffix")).isTrue()
        assertThat(participantRepository.existsByNameAndIdNot("Exists $suffix", id)).isFalse()
        assertThat(participantRepository.existsByNameAndIdNot("Exists $suffix", "other")).isTrue()
    }

    @Test
    fun `delete removes participant`() {
        val id = "del-${UUID.randomUUID().toString().take(8)}"
        participantRepository.save(Participant(id = id, name = "Delete $id", status = ParticipantStatus.ACTIVE))
        participantRepository.deleteById(id)
        assertThat(participantRepository.findById(id)).isEmpty
    }
}
