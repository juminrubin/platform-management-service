package org.jrtech.platformmanagement.persistence.table

import com.azure.core.http.HttpResponse
import com.azure.core.http.rest.PagedIterable
import com.azure.data.tables.TableClient
import com.azure.data.tables.models.ListEntitiesOptions
import com.azure.data.tables.models.TableEntity
import com.azure.data.tables.models.TableServiceException
import org.jrtech.platformmanagement.TestAudit
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.azure.core.util.Context
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AzureTableRepositoriesTest {

    @Test
    fun `participant repository save find list and delete`() {
        val table = mockTableClient()
        val store = mutableMapOf<String, TableEntity>()
        stubTableMap(table, store)

        val repo = AzureTableParticipantRepository(table)
        val saved = repo.save(
            Participant(
                id = "p1",
                name = "Acme",
                contact = "a@x.com",
                status = ParticipantStatus.ACTIVE,
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        assertThat(saved.id).isEqualTo("p1")
        assertThat(repo.findById("p1")?.name).isEqualTo("Acme")
        assertThat(repo.findByName("Acme")?.id).isEqualTo("p1")
        assertThat(repo.findByStatus(ParticipantStatus.ACTIVE)).hasSize(1)
        assertThat(repo.existsById("p1")).isTrue()
        assertThat(repo.existsByName("Acme")).isTrue()
        assertThat(repo.existsByNameAndIdNot("Acme", "p1")).isFalse()
        assertThat(repo.count()).isEqualTo(1)
        repo.deleteById("p1")
        assertThat(repo.findById("p1")).isNull()
    }

    @Test
    fun `service offering repository save filters and delete`() {
        val table = mockTableClient()
        val store = mutableMapOf<String, TableEntity>()
        stubTableMap(table, store)
        val repo = AzureTableServiceOfferingRepository(table)

        repo.save(
            ServiceOffering(
                id = "gpt",
                name = "GPT",
                category = "LLM",
                provider = "OPENAI",
                config = "{}",
                active = true,
                description = "d",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        repo.save(
            ServiceOffering(
                id = "stt",
                name = "STT",
                category = "SPEECH",
                active = false,
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        assertThat(repo.findByActiveTrue().map { it.id }).containsExactly("gpt")
        assertThat(repo.findByCategory("llm").map { it.id }).containsExactly("gpt")
        assertThat(repo.findById("gpt")?.description).isEqualTo("d")
        assertThat(repo.existsById("stt")).isTrue()
        assertThat(repo.count()).isEqualTo(2)
        repo.deleteById("stt")
        assertThat(repo.findById("stt")).isNull()
    }

    @Test
    fun `caller repository save and find with participant lookup`() {
        val table = mockTableClient()
        val store = mutableMapOf<String, TableEntity>()
        stubTableMap(table, store)
        val participants = mock<ParticipantRepository>()
        whenever(participants.findById("p1")).thenReturn(
            Participant(id = "p1", name = "P", createdBy = TestAudit.BY, updatedBy = TestAudit.BY)
        )
        val repo = AzureTableCallerRegistrationRepository(table, participants)
        val p = Participant(id = "p1", name = "P", createdBy = TestAudit.BY, updatedBy = TestAudit.BY)
        repo.save(
            ParticipantCallerRegistration(
                callerId = "c1",
                participant = p,
                status = CallerRegistrationStatus.ACTIVE,
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        assertThat(repo.findByCallerIdWithParticipant("c1")?.participant?.name).isEqualTo("P")
        assertThat(repo.findByParticipantId("p1")).hasSize(1)
        assertThat(repo.findByStatus(CallerRegistrationStatus.ACTIVE)).hasSize(1)
        assertThat(repo.findAllWithParticipant()).hasSize(1)
        assertThat(repo.existsByCallerId("c1")).isTrue()
        assertThat(repo.count()).isEqualTo(1)
        repo.deleteById("c1")
        assertThat(repo.existsById("c1")).isFalse()
    }

    @Test
    fun `entitlement repository save find filters and delete`() {
        val table = mockTableClient()
        val store = mutableMapOf<String, TableEntity>()
        stubTableMap(table, store)
        val participants = mock<ParticipantRepository>()
        val services = mock<ServiceOfferingRepository>()
        val p = Participant(id = "p1", name = "P", createdBy = TestAudit.BY, updatedBy = TestAudit.BY)
        val s = ServiceOffering(
            id = "gpt",
            name = "G",
            category = "LLM",
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY
        )
        whenever(participants.findById("p1")).thenReturn(p)
        whenever(services.findById("gpt")).thenReturn(s)

        val repo = AzureTableEntitlementRepository(table, participants, services)
        val saved = repo.save(
            ParticipantServiceEntitlement(
                participant = p,
                serviceOffering = s,
                status = EntitlementStatus.ACTIVE,
                validFrom = LocalDate.of(2026, 1, 1),
                validTo = LocalDate.of(2030, 1, 1),
                config = "{}",
                notes = "n",
                createdBy = TestAudit.BY,
                updatedBy = TestAudit.BY
            )
        )
        assertThat(repo.findByIdWithRelations(saved.id)?.notes).isEqualTo("n")
        assertThat(repo.findByParticipantId("p1")).hasSize(1)
        assertThat(repo.findByServiceOfferingId("gpt")).hasSize(1)
        assertThat(repo.findByStatus(EntitlementStatus.ACTIVE)).hasSize(1)
        assertThat(repo.existsByParticipantIdAndServiceOfferingId("p1", "gpt")).isTrue()
        assertThat(
            repo.findActiveAndValidAsOf(LocalDate.of(2026, 6, 1), EntitlementStatus.ACTIVE)
        ).hasSize(1)
        assertThat(repo.count()).isEqualTo(1)
        repo.deleteById(saved.id)
        assertThat(repo.existsById(saved.id)).isFalse()
    }

    @Test
    fun `consumption repository save with source ref index and queries`() {
        val table = mockTableClient()
        val sourceRef = mockTableClient()
        val store = mutableMapOf<String, TableEntity>()
        val refStore = mutableMapOf<String, TableEntity>()
        stubTableMap(table, store)
        stubTableMap(sourceRef, refStore)

        val p = Participant(id = "p1", name = "P", createdBy = TestAudit.BY, updatedBy = TestAudit.BY)
        val caller = ParticipantCallerRegistration(
            callerId = "user@x.com",
            participant = p,
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY
        )
        val offering = ServiceOffering(
            id = "gpt",
            name = "G",
            category = "LLM",
            createdBy = TestAudit.BY,
            updatedBy = TestAudit.BY
        )
        val callers = mock<ParticipantCallerRegistrationRepository>()
        val services = mock<ServiceOfferingRepository>()
        whenever(callers.findByCallerIdWithParticipant("user@x.com")).thenReturn(caller)
        whenever(services.findById("gpt")).thenReturn(offering)

        val repo = AzureTableConsumptionRepository(table, sourceRef, callers, services)
        val id = UUID.randomUUID()
        val saved = repo.save(
            ParticipantCallConsumption(
                id = id,
                callerRegistration = caller,
                serviceOffering = offering,
                sourceRefId = "req-1",
                consumptionData = "{}",
                capturedAt = Instant.parse("2026-01-01T00:00:00Z"),
                createdAt = Instant.parse("2026-01-01T00:00:01Z")
            )
        )
        assertThat(saved.id).isEqualTo(id)
        assertThat(repo.existsBySourceRefId("req-1")).isTrue()
        assertThat(repo.findBySourceRefIdWithRelations("req-1")?.id).isEqualTo(id)
        assertThat(repo.findByIdWithRelations(id)?.callerRegistration?.callerId).isEqualTo("user@x.com")
        assertThat(repo.findByCallerId("user@x.com")).hasSize(1)
        assertThat(repo.findByServiceOfferingId("gpt")).hasSize(1)
        assertThat(repo.findAllWithRelations()).hasSize(1)
        assertThat(repo.count()).isEqualTo(1)
        assertThat(repo.existsById(id)).isTrue()
        repo.deleteById(id)
        assertThat(repo.existsById(id)).isFalse()
        assertThat(repo.existsBySourceRefId("req-1")).isFalse()
    }

    private fun mockTableClient(): TableClient = mock()

    private fun stubTableMap(table: TableClient, store: MutableMap<String, TableEntity>) {
        fun key(pk: String, rk: String) = "$pk|$rk"

        whenever(table.upsertEntity(any())).thenAnswer { inv ->
            val e = inv.getArgument<TableEntity>(0)
            store[key(e.partitionKey, e.rowKey)] = e
            null
        }
        whenever(table.getEntity(anyString(), anyString())).thenAnswer { inv ->
            val pk = inv.getArgument<String>(0)
            val rk = inv.getArgument<String>(1)
            store[key(pk, rk)] ?: throw table404()
        }
        whenever(table.deleteEntity(anyString(), anyString())).thenAnswer { inv ->
            val pk = inv.getArgument<String>(0)
            val rk = inv.getArgument<String>(1)
            if (store.remove(key(pk, rk)) == null) throw table404()
            null
        }
        // listAll() / filtered lists → listEntities(options, timeout, context)
        whenever(
            table.listEntities(
                any(ListEntitiesOptions::class.java),
                isNull(),
                isNull()
            )
        ).thenAnswer {
            mockPaged(store.values.toList())
        }
        whenever(
            table.listEntities(
                any(ListEntitiesOptions::class.java),
                any(Duration::class.java),
                any(Context::class.java)
            )
        ).thenAnswer {
            mockPaged(store.values.toList())
        }
        whenever(
            table.listEntities(
                any(ListEntitiesOptions::class.java),
                isNull(),
                any(Context::class.java)
            )
        ).thenAnswer {
            mockPaged(store.values.toList())
        }
    }

    private fun mockPaged(list: List<TableEntity>): PagedIterable<TableEntity> {
        // Real iterable subclass that PagedIterable-typed return can be faked via mock + iterator
        val paged = mock<PagedIterable<TableEntity>>()
        whenever(paged.iterator()).thenAnswer { list.toMutableList().iterator() }
        // Kotlin Iterable.map uses iterator(); also support stream if used
        return paged
    }

    private fun table404(): TableServiceException {
        val response = mock<HttpResponse>()
        org.mockito.Mockito.lenient().whenever(response.statusCode).thenReturn(404)
        return TableServiceException("not found", response, null)
    }
}
