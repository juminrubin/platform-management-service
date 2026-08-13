package org.jrtech.platformmanagement.connectors.consumption.blob.claim

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BlobFileClaimKeys {
    fun partitionKey(inputContainer: String): String {
        val name = inputContainer.trim()
        return if (name.isEmpty()) "_" else name
    }

    /** Azure Table RowKey cannot contain `/ \\ # ?`. */
    fun rowKey(inputBlob: String): String =
        URLEncoder.encode(inputBlob.trim(), StandardCharsets.UTF_8).replace("+", "%20")
}
