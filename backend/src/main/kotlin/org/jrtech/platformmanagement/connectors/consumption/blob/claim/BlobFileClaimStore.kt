package org.jrtech.platformmanagement.connectors.consumption.blob.claim

import java.time.Duration

/**
 * Shared claim store for Avro → Parquet file conversion.
 *
 * One row per input blob. Compare-and-swap so multiple JVMs convert a given
 * (path, etag) at most once.
 */
interface BlobFileClaimStore {
    fun tryClaim(
        inputContainer: String,
        inputBlob: String,
        inputEtag: String,
        inputLength: Long?,
        outputBlob: String,
        owner: String,
        lease: Duration,
        force: Boolean = false
    ): ClaimOutcome

    fun renew(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        lease: Duration
    ): Boolean

    fun sealSucceeded(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        outputBlob: String,
        recordsWritten: Int
    ): Boolean

    fun markFailed(
        inputContainer: String,
        inputBlob: String,
        generation: Long,
        owner: String,
        error: String
    ): Boolean
}
