package org.jrtech.platformmanagement.connectors.consumption.blob.claim

import org.jrtech.platformmanagement.domain.UtcTimestamps
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryBlobFileClaimStore : BlobFileClaimStore {

    private val lock = Any()
    private val rows = ConcurrentHashMap<String, BlobFileClaim>()

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
        val key = key(inputContainer, inputBlob)
        val now = UtcTimestamps.now()
        synchronized(lock) {
            val existing = rows[key]
            if (existing == null) {
                val created = newRunning(
                    inputContainer, inputBlob, inputEtag, inputLength, outputBlob, owner, lease, 1L, now
                )
                rows[key] = created
                return ClaimOutcome.Acquired(created)
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
            val stolen = newRunning(
                inputContainer,
                inputBlob,
                inputEtag,
                inputLength,
                outputBlob,
                owner,
                lease,
                existing.generation + 1,
                now
            )
            rows[key] = stolen
            return ClaimOutcome.Acquired(stolen)
        }
    }

    override fun renew(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        lease: Duration
    ): Boolean {
        val key = key(inputContainer, inputBlob)
        synchronized(lock) {
            val existing = rows[key] ?: return false
            if (existing.generation != generation || existing.owner != owner) return false
            if (existing.status != BlobFileClaimStatus.RUNNING) return false
            rows[key] = existing.copy(
                leaseUntil = UtcTimestamps.now().plus(lease),
                version = UUID.randomUUID().toString()
            )
            return true
        }
    }

    override fun sealSucceeded(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        outputBlob: String,
        recordsWritten: Int
    ): Boolean {
        val key = key(inputContainer, inputBlob)
        synchronized(lock) {
            val existing = rows[key] ?: return false
            if (existing.generation != generation || existing.owner != owner) return false
            rows[key] = existing.copy(
                status = BlobFileClaimStatus.SUCCEEDED,
                outputBlob = outputBlob,
                recordsWritten = recordsWritten,
                lastError = null,
                finishedAt = UtcTimestamps.now(),
                version = UUID.randomUUID().toString()
            )
            return true
        }
    }

    override fun markFailed(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        error: String
    ): Boolean {
        val key = key(inputContainer, inputBlob)
        synchronized(lock) {
            val existing = rows[key] ?: return false
            if (existing.generation != generation || existing.owner != owner) return false
            rows[key] = existing.copy(
                status = BlobFileClaimStatus.FAILED,
                lastError = error,
                finishedAt = UtcTimestamps.now(),
                version = UUID.randomUUID().toString()
            )
            return true
        }
    }

    fun get(inputContainer: String, inputBlob: String): BlobFileClaim? =
        rows[key(inputContainer, inputBlob)]

    fun clear() = rows.clear()

    private fun newRunning(
        inputContainer: String,
        inputBlob: String,
        inputEtag: String,
        inputLength: Long?,
        outputBlob: String,
        owner: String,
        lease: Duration,
        generation: Long,
        now: java.time.Instant
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
            generation = generation,
            version = UUID.randomUUID().toString()
        )

    private fun key(inputContainer: String, inputBlob: String): String =
        "${BlobFileClaimKeys.partitionKey(inputContainer)}\u0000${BlobFileClaimKeys.rowKey(inputBlob)}"
}
