package com.example.participantapi.service

import com.example.participantapi.domain.ParticipantStatus
import com.example.participantapi.dto.CreateParticipantRequest
import com.example.participantapi.dto.UpdateParticipantRequest
import com.example.participantapi.exception.ConflictException
import com.example.participantapi.repository.ParticipantRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest
@Import(ParticipantService::class)
@ActiveProfiles("test")
class ParticipantServicePersistenceTest @Autowired constructor(
    private val participantService: ParticipantService,
    private val participantRepository: ParticipantRepository
) {

    @Test
    fun `create find update and delete round-trip`() {
        val id = "ps-${UUID.randomUUID().toString().take(8)}"
        val created = participantService.create(
            CreateParticipantRequest(id = id, name = "Persistence Co $id", contact = "a@b.com", status = ParticipantStatus.ACTIVE)
        )
        assertThat(participantRepository.findById(created.id)).isPresent

        val updated = participantService.update(
            created.id,
            UpdateParticipantRequest(name = "Updated $id", contact = "c@d.com", status = ParticipantStatus.SUSPENDED)
        )
        assertThat(updated.status).isEqualTo(ParticipantStatus.SUSPENDED)
        assertThat(updated.contact).isEqualTo("c@d.com")

        participantService.delete(created.id)
        assertThat(participantRepository.findById(created.id)).isEmpty
    }

    @Test
    fun `create rejects duplicate name`() {
        val suffix = UUID.randomUUID().toString().take(8)
        participantService.create(CreateParticipantRequest(id = "a-$suffix", name = "Dup Name $suffix"))
        assertThatThrownBy {
            participantService.create(CreateParticipantRequest(id = "b-$suffix", name = "Dup Name $suffix"))
        }.isInstanceOf(ConflictException::class.java)
    }
}
