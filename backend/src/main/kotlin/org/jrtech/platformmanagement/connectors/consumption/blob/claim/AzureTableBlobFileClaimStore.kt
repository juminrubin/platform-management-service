package org.jrtech.platformmanagement.connectors.consumption.blob.claim

import com.azure.data.tables.TableClient
import com.azure.data.tables.models.TableEntity
import com.azure.data.tables.models.TableServiceException
import com.azure.data.tables.models.TableEntityUpdateMode
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.persistence.getOrNull
import org.jrtech.platformmanagement.persistence.instantProp
import org.jrtech.platformmanagement.persistence.stringProp
import java.time.Duration
import java.time.Instant

class AzureTableBlobFileClaimStore(
    private val table: TableClient
) : BlobFileClaimStore {

    private val log = logger()

    override fun tryClaim(
        inputContainer: String,
        inputBlob: String,
        inputEtag: String,
        inputLength: Long?,
        outputBlob: String,
        owner: String,
        lease: Duration,
        force: Boolean
    ): ClaimOutcome {
        val pk = BlobFileClaimKeys.partitionKey(inputContainer)
        val rk = BlobFileClaimKeys.rowKey(inputBlob)
        repeat(8) {
            val existing = table.getOrNull(pk, rk)?.toClaim()
            val now = UtcTimestamps.now()
            if (existing == null) {
                val created = runningClaim(
                    inputContainer, inputBlob, inputEtag, inputLength, outputBlob, owner, lease, 1L, now
                )
                return if (insertNew(created)) {
                    ClaimOutcome.Acquired(reload(pk, rk) ?: created)
                } else {
                    return@repeat
                }
            }
            if (!force &&
                existing.status == BlobFileClaimStatus.SUCCEEDED &&
                existing.inputEtag == inputEtag
            ) {
                return ClaimOutcome.AlreadySucceeded(existing)
            }
            val held = !force &&
                existing.status == BlobFileClaimStatus.RUNNING &&
                existing.leaseUntil.isAfter(now) &&
                existing.owner != owner
            if (held) {
                return ClaimOutcome.HeldByOther(existing)
            }
            val stolen = runningClaim(
                inputContainer,
                inputBlob,
                inputEtag,
                inputLength,
                outputBlob,
                owner,
                lease,
                existing.generation + 1,
                now
            ).copy(version = existing.version)
            if (replaceIfMatch(stolen)) {
                return ClaimOutcome.Acquired(reload(pk, rk) ?: stolen)
            }
        }
        val latest = table.getOrNull(pk, rk)?.toClaim()
        return when {
            latest == null ->
                error("Unable to claim $inputContainer/$inputBlob after retries")
            latest.status == BlobFileClaimStatus.SUCCEEDED && latest.inputEtag == inputEtag && !force ->
                ClaimOutcome.AlreadySucceeded(latest)
            else -> ClaimOutcome.HeldByOther(latest)
        }
    }

    override fun renew(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        lease: Duration
    ): Boolean {
        val current = load(inputContainer, inputBlob) ?: return false
        if (current.generation != generation || current.owner != owner) return false
        if (current.status != BlobFileClaimStatus.RUNNING) return false
        val renewed = current.copy(leaseUntil = UtcTimestamps.now().plus(lease))
        return replaceIfMatch(renewed)
    }

    override fun sealSucceeded(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        outputBlob: String,
        recordsWritten: Int
    ): Boolean {
        val current = load(inputContainer, inputBlob) ?: return false
        if (current.generation != generation || current.owner != owner) return false
        val sealed = current.copy(
            status = BlobFileClaimStatus.SUCCEEDED,
            outputBlob = outputBlob,
            recordsWritten = recordsWritten,
            lastError = null,
            finishedAt = UtcTimestamps.now()
        )
        return replaceIfMatch(sealed)
    }

    override fun markFailed(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        error: String
    ): Boolean {
        val current = load(inputContainer, inputBlob) ?: return false
        if (current.generation != generation || current.owner != owner) return false
        val failed = current.copy(
            status = BlobFileClaimStatus.FAILED,
            lastError = error,
            finishedAt = UtcTimestamps.now()
        )
        return replaceIfMatch(failed)
    }

    private fun load(inputContainer: String, inputBlob: String): BlobFileClaim? =
        table.getOrNull(
            BlobFileClaimKeys.partitionKey(inputContainer),
            BlobFileClaimKeys.rowKey(inputBlob)
        )?.toClaim()

    private fun reload(pk: String, rk: String): BlobFileClaim? = table.getOrNull(pk, rk)?.toClaim()

    private fun insertNew(claim: BlobFileClaim): Boolean =
        try {
            table.createEntity(claim.toEntity())
            true
        } catch (ex: TableServiceException) {
            if (ex.response?.statusCode == 409) {
                false
            } else {
                throw ex
            }
        }

    private fun replaceIfMatch(claim: BlobFileClaim): Boolean {
        val pk = BlobFileClaimKeys.partitionKey(claim.inputContainer)
        val rk = BlobFileClaimKeys.rowKey(claim.inputBlob)
        val existing = table.getOrNull(pk, rk) ?: return false
        val current = existing.toClaim()
        if (current.generation != claim.generation || current.owner != claim.owner) {
            return false
        }
        applyClaimProperties(existing, claim)
        return try {
            table.updateEntityWithResponse(
                existing,
                TableEntityUpdateMode.REPLACE,
                true,
                null,
                com.azure.core.util.Context.NONE
            )
            true
        } catch (ex: TableServiceException) {
            val code = ex.response?.statusCode
            if (code == 412 || code == 409 || code == 404) {
                log.debug("Claim CAS lost for {}/{}: HTTP {}", claim.inputContainer, claim.inputBlob, code)
                false
            } else {
                throw ex
            }
        }
    }

    private fun runningClaim(
        inputContainer: String,
        inputBlob: String,
        inputEtag: String,
        inputLength: Long?,
        outputBlob: String,
        owner: String,
        lease: Duration,
        generation: Long,
        now: Instant
    ): BlobFileClaim =
        BlobFileClaim(
            inputContainer = inputContainer,
            inputBlob = inputBlob,
            inputEtag = inputEtag,
            inputLength = inputLength,
            outputBlob = outputBlob,
            status = BlobFileClaimStatus.RUNNING,
            owner = owner,
            leaseUntil = now.plus(lease),
            generation = generation
        )
}

private fun BlobFileClaim.toEntity(): TableEntity {
    val entity = TableEntity(
        BlobFileClaimKeys.partitionKey(inputContainer),
        BlobFileClaimKeys.rowKey(inputBlob)
    )
    applyClaimProperties(entity, this)
    return entity
}

private fun applyClaimProperties(entity: TableEntity, claim: BlobFileClaim) {
    entity.addProperty("inputBlob", claim.inputBlob)
    entity.addProperty("inputEtag", claim.inputEtag)
    entity.addProperty("inputLength", claim.inputLength)
    entity.addProperty("outputBlob", claim.outputBlob)
    entity.addProperty("status", claim.status.name)
    entity.addProperty("owner", claim.owner)
    entity.addProperty("leaseUntil", claim.leaseUntil.toString())
    entity.addProperty("generation", claim.generation)
    entity.addProperty("recordsWritten", claim.recordsWritten.toLong())
    entity.addProperty("lastError", claim.lastError)
    entity.addProperty("finishedAt", claim.finishedAt?.toString())
}

private fun TableEntity.toClaim(): BlobFileClaim =
    BlobFileClaim(
        inputContainer = partitionKey,
        inputBlob = stringProp("inputBlob") ?: rowKey,
        inputEtag = stringProp("inputEtag") ?: "",
        inputLength = longProp("inputLength"),
        outputBlob = stringProp("outputBlob") ?: "",
        status = runCatching {
            BlobFileClaimStatus.valueOf(stringProp("status") ?: "RUNNING")
        }.getOrDefault(BlobFileClaimStatus.RUNNING),
        owner = stringProp("owner") ?: "",
        leaseUntil = instantProp("leaseUntil", Instant.EPOCH),
        generation = longProp("generation") ?: 0L,
        recordsWritten = (longProp("recordsWritten") ?: 0L).toInt(),
        lastError = stringProp("lastError"),
        finishedAt = stringProp("finishedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() },
        version = eTag ?: ""
    )

private fun TableEntity.longProp(name: String): Long? {
    val v = getProperty(name) ?: return null
    return when (v) {
        is Long -> v
        is Int -> v.toLong()
        is Number -> v.toLong()
        else -> v.toString().toLongOrNull()
    }
}
