package org.jrtech.platformmanagement.repository

import org.springframework.boot.test.context.SpringBootTest

import org.jrtech.platformmanagement.domain.ServiceOffering
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import org.jrtech.platformmanagement.TestAudit

@SpringBootTest
@ActiveProfiles("test")
class ServiceOfferingRepositoryTest @Autowired constructor(
    private val serviceOfferingRepository: ServiceOfferingRepository
) {

    @Test
    fun `save and findById returns offering with config`() {
        val id = "model-${UUID.randomUUID().toString().take(8)}"
        serviceOfferingRepository.save(
            ServiceOffering(
                id = id,
                name = "Test Model",
                description = "desc",
                category = "LLM",
                config = """{"default_max_tpm":1000}""",
                active = true,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY)
        )

        val found = serviceOfferingRepository.findById(id)!!
        assertThat(found.name).isEqualTo("Test Model")
        assertThat(found.provider).isEqualTo("SYSTEM")
        assertThat(found.config).contains("default_max_tpm")
    }

    @Test
    fun `findByActiveTrue returns only active offerings`() {
        val suffix = UUID.randomUUID().toString().take(8)
        serviceOfferingRepository.save(ServiceOffering(id = "act-$suffix", name = "A", category = "LLM", active = true,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY))
        serviceOfferingRepository.save(ServiceOffering(id = "ina-$suffix", name = "B", category = "LLM", active = false,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY))

        val active = serviceOfferingRepository.findByActiveTrue()
        assertThat(active.map { it.id }).contains("act-$suffix")
        assertThat(active.map { it.id }).doesNotContain("ina-$suffix")
    }

    @Test
    fun `findByCategory returns offerings in category`() {
        val suffix = UUID.randomUUID().toString().take(8)
        serviceOfferingRepository.save(ServiceOffering(id = "sp-$suffix", name = "Speech", category = "SPEECH", active = true,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY))
        serviceOfferingRepository.save(ServiceOffering(id = "ll-$suffix", name = "LLM", category = "LLM", active = true,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY))

        assertThat(serviceOfferingRepository.findByCategory("SPEECH").map { it.id }).contains("sp-$suffix")
    }
}
