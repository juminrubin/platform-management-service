package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

/** Serializes processed rows to a lake file (bytes). Not a Spring bean. */
interface OutputRecordWriter<T> {
    fun write(rows: List<T>): ByteArray
}
