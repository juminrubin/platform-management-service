package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

/**
 * Maps one filtered input record to one output row (CSV-shaped / lake row).
 *
 * Not a Spring bean — each file pipeline constructs its own processor.
 */
abstract class RecordProcessor<IN, OUT> {
    /** Return the output row, or null to drop the record. */
    abstract fun process(record: IN): OUT?

    fun processAll(records: Iterable<IN>): List<OUT> =
        records.mapNotNull { process(it) }
}
