package org.jrtech.platformmanagement.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@Import(DataSourceLoader::class)
@ActiveProfiles("test")
class DataSourceLoaderTest @Autowired constructor(
    private val dataSourceLoader: DataSourceLoader,
    private val serviceOfferingRepository: ServiceOfferingRepository,
    private val participantRepository: ParticipantRepository,
    private val callerRegistrationRepository: ParticipantCallerRegistrationRepository,
    private val entitlementRepository: ParticipantServiceEntitlementRepository
) {

    @Test
    fun `loadOnStartup seeds services participants callers and entitlements`() {
        dataSourceLoader.loadOnStartup()

        assertThat(serviceOfferingRepository.findById("gpt-5.1")).isPresent
        assertThat(serviceOfferingRepository.findById("gpt-5.4-mini")).isPresent
        assertThat(serviceOfferingRepository.findById("az-whisper-stt")).isPresent

        val marketing = participantRepository.findById("P001").orElseThrow()
        assertThat(marketing.name).isEqualTo("Marketing Department")
        assertThat(marketing.status).isEqualTo(ParticipantStatus.ACTIVE)
        assertThat(marketing.contact).isEqualTo("sky.walker@company.com")

        val production = participantRepository.findById("P002").orElseThrow()
        assertThat(production.name).isEqualTo("Production Department")

        assertThat(
            callerRegistrationRepository.findByCallerIdWithParticipant(
                "11111111-2222-3333-4444-555555555555"
            )
        ).isNotNull
        assertThat(
            callerRegistrationRepository.findByCallerIdWithParticipant("sky.walker@company.com")!!.status
        ).isEqualTo(CallerRegistrationStatus.ACTIVE)

        assertThat(
            entitlementRepository.existsByParticipantIdAndServiceOfferingId("P001", "gpt-5.1")
        ).isTrue()
        assertThat(
            entitlementRepository.existsByParticipantIdAndServiceOfferingId("P002", "az-whisper-stt")
        ).isTrue()

        val offering = serviceOfferingRepository.findById("gpt-5.1").orElseThrow()
        assertThat(offering.provider).isEqualTo("OPENAI")
        assertThat(offering.category).isEqualTo("Language Models")
        assertThat(offering.active).isTrue()
    }

    @Test
    fun `loadOnStartup is idempotent and does not duplicate rows`() {
        dataSourceLoader.loadOnStartup()
        val servicesAfterFirst = serviceOfferingRepository.count()
        val participantsAfterFirst = participantRepository.count()
        val callersAfterFirst = callerRegistrationRepository.count()
        val entitlementsAfterFirst = entitlementRepository.count()

        dataSourceLoader.loadOnStartup()

        assertThat(serviceOfferingRepository.count()).isEqualTo(servicesAfterFirst)
        assertThat(participantRepository.count()).isEqualTo(participantsAfterFirst)
        assertThat(callerRegistrationRepository.count()).isEqualTo(callersAfterFirst)
        assertThat(entitlementRepository.count()).isEqualTo(entitlementsAfterFirst)
    }

    @Test
    fun `loadOnStartup skips missing resource without failing`() {
        val beforeServices = serviceOfferingRepository.count()
        val beforeParticipants = participantRepository.count()
        val missingLoader = DataSourceLoader(
            properties = DataSourceLoaderProperties(
                enabled = true,
                location = "classpath:does-not-exist-datasource.json"
            ),
            resourceLoader = DefaultResourceLoader(),
            serviceOfferingRepository = serviceOfferingRepository,
            participantRepository = participantRepository,
            callerRegistrationRepository = callerRegistrationRepository,
            entitlementRepository = entitlementRepository
        )

        missingLoader.loadOnStartup()

        assertThat(serviceOfferingRepository.count()).isEqualTo(beforeServices)
        assertThat(participantRepository.count()).isEqualTo(beforeParticipants)
    }

    @Test
    fun `seeded entitlement status and empty config normalize correctly`() {
        dataSourceLoader.loadOnStartup()

        val entitlement = entitlementRepository.findByParticipantId("P001")
            .first { it.serviceOffering.id == "gpt-5.1" }

        assertThat(entitlement.status).isEqualTo(EntitlementStatus.ACTIVE)
        assertThat(entitlement.config).isEqualTo("{}")
        assertThat(entitlement.notes).isEqualTo("Standard LLM package")
        assertThat(entitlement.validFrom.toString()).isEqualTo("2026-01-01")
        assertThat(entitlement.validTo.toString()).isEqualTo("2030-01-01")
    }
}
