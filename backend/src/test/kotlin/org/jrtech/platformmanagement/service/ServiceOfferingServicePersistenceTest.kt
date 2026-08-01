package org.jrtech.platformmanagement.service

import org.jrtech.platformmanagement.dto.CreateServiceOfferingRequest
import org.jrtech.platformmanagement.dto.UpdateServiceOfferingRequest
import org.jrtech.platformmanagement.exception.ConflictException
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@DataJpaTest
@Import(ServiceOfferingService::class)
@ActiveProfiles("test")
class ServiceOfferingServicePersistenceTest @Autowired constructor(
    private val serviceOfferingService: ServiceOfferingService,
    private val serviceOfferingRepository: ServiceOfferingRepository
) {

    @Test
    fun `create list update and delete round-trip`() {
        val id = "model-${UUID.randomUUID().toString().take(8)}"
        val created = serviceOfferingService.create(
            CreateServiceOfferingRequest(
                id = id,
                name = "Model",
                category = "llm",
                config = """{"default_max_tpm":1000}""",
                active = true
            )
        )
        assertThat(created.category).isEqualTo("LLM")
        assertThat(created.provider).isEqualTo("SYSTEM")
        assertThat(serviceOfferingRepository.existsById(id)).isTrue()

        val updated = serviceOfferingService.update(
            id,
            UpdateServiceOfferingRequest(
                name = "Model 2",
                description = null,
                category = "speech",
                provider = "azure",
                config = "{}",
                active = false
            )
        )
        assertThat(updated.active).isFalse()
        assertThat(updated.category).isEqualTo("SPEECH")
        assertThat(updated.provider).isEqualTo("AZURE")

        serviceOfferingService.delete(id)
        assertThat(serviceOfferingRepository.findById(id)).isEmpty
    }

    @Test
    fun `create rejects duplicate id`() {
        val id = "dup-${UUID.randomUUID().toString().take(8)}"
        serviceOfferingService.create(CreateServiceOfferingRequest(id = id, name = "One", category = "LLM"))
        assertThatThrownBy {
            serviceOfferingService.create(CreateServiceOfferingRequest(id = id, name = "Two", category = "LLM"))
        }.isInstanceOf(ConflictException::class.java)
    }
}
