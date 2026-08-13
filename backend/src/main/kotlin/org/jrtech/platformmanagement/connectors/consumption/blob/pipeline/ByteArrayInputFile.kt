package org.jrtech.platformmanagement.connectors.consumption.blob.pipeline

import org.apache.parquet.io.DelegatingSeekableInputStream
import org.apache.parquet.io.InputFile
import org.apache.parquet.io.SeekableInputStream
import java.io.ByteArrayInputStream

/** In-memory Parquet [InputFile] (no Hadoop Path). */
internal class ByteArrayInputFile(
    private val data: ByteArray
) : InputFile {
    override fun getLength(): Long = data.size.toLong()

    override fun newStream(): SeekableInputStream {
        val inner = ByteArrayInputStream(data)
        return object : DelegatingSeekableInputStream(inner) {
            private var pos = 0L
            override fun getPos(): Long = pos
            override fun seek(newPos: Long) {
                inner.reset()
                val skipped = inner.skip(newPos)
                pos = skipped
            }
            override fun read(): Int {
                val b = super.read()
                if (b >= 0) pos++
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) pos += n
                return n
            }
        }
    }
}
