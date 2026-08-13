package org.jrtech.platformmanagement.persistence.table

import com.azure.data.tables.TableClient
import com.azure.data.tables.models.TableEntity
import org.jrtech.platformmanagement.domain.CallerRegistrationStatus
import org.jrtech.platformmanagement.domain.EntitlementStatus
import org.jrtech.platformmanagement.domain.Participant
import org.jrtech.platformmanagement.domain.ParticipantCallConsumption
import org.jrtech.platformmanagement.domain.ParticipantCallerRegistration
import org.jrtech.platformmanagement.domain.ParticipantServiceEntitlement
import org.jrtech.platformmanagement.domain.ParticipantStatus
import org.jrtech.platformmanagement.domain.ServiceOffering
import org.jrtech.platformmanagement.domain.StatusParsing
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.persistence.AzureTableKeys
import org.jrtech.platformmanagement.persistence.boolProp
import org.jrtech.platformmanagement.persistence.deleteIfExists
import org.jrtech.platformmanagement.persistence.getOrNull
import org.jrtech.platformmanagement.persistence.instantProp
import org.jrtech.platformmanagement.persistence.listAll
import org.jrtech.platformmanagement.persistence.localDateProp
import org.jrtech.platformmanagement.persistence.requireString
import org.jrtech.platformmanagement.persistence.stringProp
import org.jrtech.platformmanagement.persistence.upsert
import org.jrtech.platformmanagement.persistence.uuidProp
import org.jrtech.platformmanagement.repository.ParticipantCallConsumptionRepository
import org.jrtech.platformmanagement.repository.ParticipantCallerRegistrationRepository
import org.jrtech.platformmanagement.repository.ParticipantRepository
import org.jrtech.platformmanagement.repository.ParticipantServiceEntitlementRepository
import org.jrtech.platformmanagement.repository.ServiceOfferingRepository
import java.time.LocalDate
import java.util.UUID

class AzureTableParticipantRepository(
    private val table: TableClient
) : ParticipantRepository {
    override fun findById(id: String): Participant? =
        table.getOrNull(AzureTableKeys.PK_PARTICIPANT, id.trim())?.toParticipant()
    override fun findAll(): List<Participant> = table.listAll().map { it.toParticipant() }
    override fun findByStatus(status: ParticipantStatus): List<Participant> =
        findAll().filter { it.status == status }
    override fun findByName(name: String): Participant? =
        findAll().firstOrNull { it.name == name }
    override fun existsById(id: String): Boolean = findById(id) != null
    override fun existsByName(name: String): Boolean = findByName(name) != null
    override fun existsByNameAndIdNot(name: String, id: String): Boolean =
        findAll().any { it.name == name && it.id != id }
    override fun save(entity: Participant): Participant {
        val now = UtcTimestamps.now()
        val existing = findById(entity.id)
        val toSave = entity.copy(
            createdAt = existing?.createdAt ?: entity.createdAt,
            updatedAt = now
        )
        table.upsert(toSave.toEntity())
        return toSave
    }
    override fun deleteById(id: String) {
        table.deleteIfExists(AzureTableKeys.PK_PARTICIPANT, id.trim())
    }
    override fun count(): Long = table.listAll().size.toLong()
}

class AzureTableServiceOfferingRepository(
    private val table: TableClient
) : ServiceOfferingRepository {
    override fun findById(id: String): ServiceOffering? =
        table.getOrNull(AzureTableKeys.PK_SERVICE, AzureTableKeys.encode(id))?.toServiceOffering()
    override fun findAll(): List<ServiceOffering> = table.listAll().map { it.toServiceOffering() }
    override fun findByActiveTrue(): List<ServiceOffering> = findAll().filter { it.active }
    override fun findByCategory(category: String): List<ServiceOffering> =
        findAll().filter { it.category.equals(category, ignoreCase = true) }
    override fun existsById(id: String): Boolean = findById(id) != null
    override fun save(entity: ServiceOffering): ServiceOffering {
        val now = UtcTimestamps.now()
        val existing = findById(entity.id)
        val toSave = entity.copy(
            createdAt = existing?.createdAt ?: entity.createdAt,
            updatedAt = now
        )
        table.upsert(toSave.toEntity())
        return toSave
    }
    override fun deleteById(id: String) {
        table.deleteIfExists(AzureTableKeys.PK_SERVICE, AzureTableKeys.encode(id))
    }
    override fun count(): Long = table.listAll().size.toLong()
}

class AzureTableCallerRegistrationRepository(
    private val table: TableClient,
    private val participants: ParticipantRepository
) : ParticipantCallerRegistrationRepository {
    override fun findByCallerIdWithParticipant(callerId: String): ParticipantCallerRegistration? {
        val entity = table.getOrNull(AzureTableKeys.PK_CALLER, callerId.trim()) ?: return null
        return entity.toCaller(participants)
    }
    override fun findAllWithParticipant(): List<ParticipantCallerRegistration> =
        table.listAll().mapNotNull { it.toCaller(participants) }
    override fun findByParticipantId(participantId: String): List<ParticipantCallerRegistration> =
        findAllWithParticipant().filter { it.participant.id == participantId }
    override fun findByStatus(status: CallerRegistrationStatus): List<ParticipantCallerRegistration> =
        findAllWithParticipant().filter { it.status == status }
    override fun existsByCallerId(callerId: String): Boolean =
        table.getOrNull(AzureTableKeys.PK_CALLER, callerId.trim()) != null
    override fun existsById(callerId: String): Boolean = existsByCallerId(callerId)
    override fun save(entity: ParticipantCallerRegistration): ParticipantCallerRegistration {
        val now = UtcTimestamps.now()
        val existing = findByCallerIdWithParticipant(entity.callerId)
        val toSave = entity.copy(
            createdAt = existing?.createdAt ?: entity.createdAt,
            updatedAt = now
        )
        table.upsert(toSave.toEntity())
        return findByCallerIdWithParticipant(toSave.callerId) ?: toSave
    }
    override fun deleteById(callerId: String) {
        table.deleteIfExists(AzureTableKeys.PK_CALLER, callerId.trim())
    }
    override fun count(): Long = table.listAll().size.toLong()
}

class AzureTableEntitlementRepository(
    private val table: TableClient,
    private val participants: ParticipantRepository,
    private val services: ServiceOfferingRepository
) : ParticipantServiceEntitlementRepository {
    override fun findByIdWithRelations(id: UUID): ParticipantServiceEntitlement? =
        table.listAll().mapNotNull { it.toEntitlement(participants, services) }
            .firstOrNull { it.id == id }
    override fun findAllWithRelations(): List<ParticipantServiceEntitlement> =
        table.listAll().mapNotNull { it.toEntitlement(participants, services) }
    override fun findByParticipantId(participantId: String): List<ParticipantServiceEntitlement> =
        table.listEntities(
            com.azure.data.tables.models.ListEntitiesOptions()
                .setFilter("PartitionKey eq '${participantId.trim().replace("'", "''")}'"),
            null,
            null
        ).mapNotNull { it.toEntitlement(participants, services) }
    override fun findByServiceOfferingId(serviceOfferingId: String): List<ParticipantServiceEntitlement> =
        findAllWithRelations().filter { it.serviceOffering.id == serviceOfferingId }
    override fun findByStatus(status: EntitlementStatus): List<ParticipantServiceEntitlement> =
        findAllWithRelations().filter { it.status == status }
    override fun findActiveAndValidAsOf(
        asOf: LocalDate,
        status: EntitlementStatus
    ): List<ParticipantServiceEntitlement> =
        findAllWithRelations().filter { e ->
            e.status == status &&
                !e.validFrom.isAfter(asOf) &&
                (e.validTo == null || !e.validTo!!.isBefore(asOf))
        }
    override fun existsByParticipantIdAndServiceOfferingId(
        participantId: String,
        serviceOfferingId: String
    ): Boolean =
        table.getOrNull(participantId.trim(), serviceOfferingId.trim()) != null
    override fun existsById(id: UUID): Boolean = findByIdWithRelations(id) != null
    override fun save(entity: ParticipantServiceEntitlement): ParticipantServiceEntitlement {
        val now = UtcTimestamps.now()
        val existing = table.getOrNull(entity.participant.id, entity.serviceOffering.id)
            ?.toEntitlement(participants, services)
        val toSave = entity.copy(
            id = existing?.id ?: entity.id,
            createdAt = existing?.createdAt ?: entity.createdAt,
            updatedAt = now
        )
        table.upsert(toSave.toEntity())
        return findByParticipantId(toSave.participant.id)
            .first { it.serviceOffering.id == toSave.serviceOffering.id }
    }
    override fun deleteById(id: UUID) {
        val existing = findByIdWithRelations(id) ?: return
        table.deleteIfExists(existing.participant.id, existing.serviceOffering.id)
    }
    override fun count(): Long = table.listAll().size.toLong()
}

class AzureTableConsumptionRepository(
    private val table: TableClient,
    private val sourceRefTable: TableClient,
    private val callers: ParticipantCallerRegistrationRepository,
    private val services: ServiceOfferingRepository
) : ParticipantCallConsumptionRepository {
    override fun existsBySourceRefId(sourceRefId: String): Boolean =
        sourceRefTable.getOrNull(AzureTableKeys.PK_SOURCE_REF, sourceRefId.trim()) != null
    override fun existsById(id: UUID): Boolean =
        // Consumptions are partitioned by callerId; scan for id (acceptable at moderate volume).
        findByIdWithRelations(id) != null
    override fun findBySourceRefIdWithRelations(sourceRefId: String): ParticipantCallConsumption? {
        val ref = sourceRefTable.getOrNull(AzureTableKeys.PK_SOURCE_REF, sourceRefId.trim())
            ?: return null
        val id = ref.uuidProp("consumptionId") ?: return null
        val callerId = ref.stringProp("callerId") ?: return null
        return load(callerId, id)
    }
    override fun findByIdWithRelations(id: UUID): ParticipantCallConsumption? =
        table.listAll().firstOrNull { it.rowKey == id.toString() }?.toConsumption(callers, services)
    override fun findAllWithRelations(): List<ParticipantCallConsumption> =
        table.listAll().mapNotNull { it.toConsumption(callers, services) }
    override fun findByCallerId(callerId: String): List<ParticipantCallConsumption> =
        table.listEntities(
            com.azure.data.tables.models.ListEntitiesOptions()
                .setFilter("PartitionKey eq '${callerId.trim().replace("'", "''")}'"),
            null,
            null
        ).mapNotNull { it.toConsumption(callers, services) }
    override fun findByServiceOfferingId(serviceOfferingId: String): List<ParticipantCallConsumption> =
        findAllWithRelations().filter { it.serviceOffering.id == serviceOfferingId }
    override fun save(entity: ParticipantCallConsumption): ParticipantCallConsumption {
        table.upsert(entity.toEntity())
        entity.sourceRefId?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
            val index = TableEntity(AzureTableKeys.PK_SOURCE_REF, ref)
            index.addProperty("consumptionId", entity.id.toString())
            index.addProperty("callerId", entity.callerRegistration.callerId)
            sourceRefTable.upsert(index)
        }
        return findByCallerId(entity.callerRegistration.callerId)
            .first { it.id == entity.id }
    }
    override fun deleteById(id: UUID) {
        val existing = findByIdWithRelations(id) ?: return
        table.deleteIfExists(existing.callerRegistration.callerId, id.toString())
        existing.sourceRefId?.let {
            sourceRefTable.deleteIfExists(AzureTableKeys.PK_SOURCE_REF, it)
        }
    }
    override fun count(): Long = table.listAll().size.toLong()
    private fun load(callerId: String, id: UUID): ParticipantCallConsumption? =
        table.getOrNull(callerId, id.toString())?.toConsumption(callers, services)
}

/* ─── mapping helpers ───────────────────────────────────────────────────── */

private fun Participant.toEntity(): TableEntity {
    val e = TableEntity(AzureTableKeys.PK_PARTICIPANT, id)
    e.addProperty("name", name)
    e.addProperty("contact", contact)
    e.addProperty("status", status.name)
    e.addProperty("createdBy", createdBy)
    e.addProperty("updatedBy", updatedBy)
    e.addProperty("createdAt", createdAt.toString())
    e.addProperty("updatedAt", updatedAt.toString())
    return e
}

private fun TableEntity.toParticipant(): Participant =
    Participant(
        id = rowKey,
        name = requireString("name"),
        contact = stringProp("contact"),
        status = StatusParsing.participantStatus(stringProp("status")),
        createdBy = stringProp("createdBy") ?: "SYSTEM",
        updatedBy = stringProp("updatedBy") ?: "SYSTEM",
        createdAt = instantProp("createdAt"),
        updatedAt = instantProp("updatedAt")
    )

private fun ServiceOffering.toEntity(): TableEntity {
    val e = TableEntity(AzureTableKeys.PK_SERVICE, AzureTableKeys.encode(id))
    e.addProperty("name", name)
    e.addProperty("description", description)
    e.addProperty("category", category)
    e.addProperty("provider", provider)
    e.addProperty("config", config)
    e.addProperty("active", active)
    e.addProperty("createdBy", createdBy)
    e.addProperty("updatedBy", updatedBy)
    e.addProperty("createdAt", createdAt.toString())
    e.addProperty("updatedAt", updatedAt.toString())
    return e
}

private fun TableEntity.toServiceOffering(): ServiceOffering =
    ServiceOffering(
        id = AzureTableKeys.decode(rowKey),
        name = requireString("name"),
        description = stringProp("description"),
        category = requireString("category"),
        provider = stringProp("provider") ?: ServiceOffering.DEFAULT_PROVIDER,
        config = stringProp("config") ?: "{}",
        active = boolProp("active", true),
        createdBy = stringProp("createdBy") ?: "SYSTEM",
        updatedBy = stringProp("updatedBy") ?: "SYSTEM",
        createdAt = instantProp("createdAt"),
        updatedAt = instantProp("updatedAt")
    )

private fun ParticipantCallerRegistration.toEntity(): TableEntity {
    val e = TableEntity(AzureTableKeys.PK_CALLER, callerId)
    e.addProperty("participantId", participant.id)
    e.addProperty("status", status.name)
    e.addProperty("createdBy", createdBy)
    e.addProperty("updatedBy", updatedBy)
    e.addProperty("createdAt", createdAt.toString())
    e.addProperty("updatedAt", updatedAt.toString())
    return e
}

private fun TableEntity.toCaller(
    participants: ParticipantRepository
): ParticipantCallerRegistration? {
    val participantId = stringProp("participantId") ?: return null
    val participant = participants.findById(participantId) ?: Participant(
        id = participantId,
        name = participantId,
        createdBy = "SYSTEM",
        updatedBy = "SYSTEM"
    )
    return ParticipantCallerRegistration(
        callerId = rowKey,
        participant = participant,
        status = StatusParsing.callerRegistrationStatus(stringProp("status")),
        createdBy = stringProp("createdBy") ?: "SYSTEM",
        updatedBy = stringProp("updatedBy") ?: "SYSTEM",
        createdAt = instantProp("createdAt"),
        updatedAt = instantProp("updatedAt")
    )
}

private fun ParticipantServiceEntitlement.toEntity(): TableEntity {
    val e = TableEntity(participant.id, AzureTableKeys.encode(serviceOffering.id))
    e.addProperty("id", id.toString())
    e.addProperty("status", status.name)
    e.addProperty("validFrom", validFrom.toString())
    e.addProperty("validTo", validTo?.toString())
    e.addProperty("config", config)
    e.addProperty("notes", notes)
    e.addProperty("createdBy", createdBy)
    e.addProperty("updatedBy", updatedBy)
    e.addProperty("createdAt", createdAt.toString())
    e.addProperty("updatedAt", updatedAt.toString())
    return e
}

private fun TableEntity.toEntitlement(
    participants: ParticipantRepository,
    services: ServiceOfferingRepository
): ParticipantServiceEntitlement? {
    val participant = participants.findById(partitionKey) ?: return null
    val offering = services.findById(AzureTableKeys.decode(rowKey)) ?: return null
    return ParticipantServiceEntitlement(
        id = uuidProp("id") ?: UUID.randomUUID(),
        participant = participant,
        serviceOffering = offering,
        status = StatusParsing.entitlementStatus(stringProp("status")),
        validFrom = localDateProp("validFrom") ?: LocalDate.EPOCH,
        validTo = localDateProp("validTo"),
        config = stringProp("config") ?: "{}",
        notes = stringProp("notes"),
        createdBy = stringProp("createdBy") ?: "SYSTEM",
        updatedBy = stringProp("updatedBy") ?: "SYSTEM",
        createdAt = instantProp("createdAt"),
        updatedAt = instantProp("updatedAt")
    )
}

private fun ParticipantCallConsumption.toEntity(): TableEntity {
    val e = TableEntity(callerRegistration.callerId, id.toString())
    e.addProperty("serviceOfferingId", serviceOffering.id)
    e.addProperty("sourceRefId", sourceRefId)
    e.addProperty("consumptionData", consumptionData)
    e.addProperty("capturedAt", capturedAt.toString())
    e.addProperty("createdAt", createdAt.toString())
    return e
}

private fun TableEntity.toConsumption(
    callers: ParticipantCallerRegistrationRepository,
    services: ServiceOfferingRepository
): ParticipantCallConsumption? {
    val caller = callers.findByCallerIdWithParticipant(partitionKey) ?: return null
    val offeringId = stringProp("serviceOfferingId") ?: return null
    val offering = services.findById(offeringId) ?: return null
    return ParticipantCallConsumption(
        id = UUID.fromString(rowKey),
        callerRegistration = caller,
        serviceOffering = offering,
        sourceRefId = stringProp("sourceRefId"),
        consumptionData = stringProp("consumptionData") ?: "{}",
        capturedAt = instantProp("capturedAt"),
        createdAt = instantProp("createdAt")
    )
}
