package org.jrtech.platformmanagement.connectors.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConnectorLogBufferTest {

    @Test
    fun `snapshot stays within max bytes with newest first`() {
        val max = 512
        val buffer = ConnectorLogBuffer(maxBytes = max)
        repeat(200) { i ->
            buffer.info("line-$i " + "x".repeat(40))
        }
        val snap = buffer.snapshot()
        assertThat(snap.maxBytes).isEqualTo(max)
        assertThat(snap.bytes).isLessThanOrEqualTo(max)
        assertThat(snap.lineCount).isEqualTo(snap.lines.size)
        assertThat(snap.lineCount).isGreaterThan(0)
        // Descending: newest entry at index 0
        assertThat(snap.lines.first()).contains("line-199")
    }

    @Test
    fun `new entries are prepended at index 0`() {
        val buffer = ConnectorLogBuffer(maxBytes = 8_192)
        buffer.info("first")
        buffer.info("second")
        buffer.info("third")
        val snap = buffer.snapshot()
        assertThat(snap.lineCount).isEqualTo(3)
        assertThat(snap.lines[0]).contains("third")
        assertThat(snap.lines[1]).contains("second")
        assertThat(snap.lines[2]).contains("first")
    }

    @Test
    fun `empty buffer snapshot is empty`() {
        val snap = ConnectorLogBuffer().snapshot()
        assertThat(snap.lineCount).isZero()
        assertThat(snap.bytes).isZero()
        assertThat(snap.maxBytes).isEqualTo(ConnectorLogBuffer.DEFAULT_MAX_BYTES)
        assertThat(snap.lines).isEmpty()
    }

    @Test
    fun `warn and error levels are retained newest first`() {
        val buffer = ConnectorLogBuffer(maxBytes = 8_192)
        buffer.warn("slow response")
        buffer.error("graph down")
        val snap = buffer.snapshot()
        assertThat(snap.lineCount).isEqualTo(2)
        assertThat(snap.lines[0]).contains("ERROR").contains("graph down")
        assertThat(snap.lines[1]).contains("WARN").contains("slow response")
    }

    @Test
    fun `clear empties the buffer`() {
        val buffer = ConnectorLogBuffer()
        buffer.info("keep me")
        assertThat(buffer.snapshot().lineCount).isEqualTo(1)
        buffer.clear()
        assertThat(buffer.snapshot().lineCount).isZero()
        assertThat(buffer.snapshot().bytes).isZero()
    }

    @Test
    fun `oversized single line is truncated to fit cap`() {
        val max = 120
        val buffer = ConnectorLogBuffer(maxBytes = max)
        buffer.info("Y".repeat(500))
        val snap = buffer.snapshot()
        assertThat(snap.lineCount).isEqualTo(1)
        assertThat(snap.bytes).isLessThanOrEqualTo(max)
        assertThat(snap.lines.single()).contains("…")
    }

    @Test
    fun `default max bytes is 32KiB`() {
        assertThat(ConnectorLogBuffer.DEFAULT_MAX_BYTES).isEqualTo(32 * 1024)
    }
}
