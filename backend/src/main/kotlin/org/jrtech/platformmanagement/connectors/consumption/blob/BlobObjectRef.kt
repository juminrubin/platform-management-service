package org.jrtech.platformmanagement.connectors.consumption.blob

/**
 * A blob path inside the configured container (hierarchical namespace path).
 */
data class BlobObjectRef(
    /** Full path within the container, e.g. `2024/01/15/14_30_00.avro`. */
    val name: String,
    val size: Long? = null
)
