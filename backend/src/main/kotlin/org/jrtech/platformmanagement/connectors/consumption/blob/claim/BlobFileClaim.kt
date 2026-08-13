package org.jrtech.platformmanagement.connectors.consumption.blob.claim

import java.time.Instant

enum class BlobFileClaimStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class BlobFileClaim(
    val inputContainer: String,
    val inputBlob: String,
    val inputEtag: String,
    val inputLength: Long?,
    val outputBlob: String,
    val status: BlobFileClaimStatus,
    val owner: String,
    val leaseUntil: Instant,
    val generation: Long,
    val recordsWritten: Int = 0,
    val lastError: String? = null,
    val finishedAt: Instant? = null,
    /** Table ETag for If-Match; opaque token in the in-memory store. */
    val version: String = ""
)

sealed class ClaimOutcome {
    data class Acquired(val claim: BlobFileClaim) : ClaimOutcome()
    data class AlreadySucceeded(val claim: BlobFileClaim) : ClaimOutcome()
    data class HeldByOther(val claim: BlobFileClaim) : ClaimOutcome()
}
