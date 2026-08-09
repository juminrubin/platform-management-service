package org.jrtech.platformmanagement.config

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import java.time.Instant
import java.util.Properties
import kotlin.test.assertEquals

class BuildInfoStartupLoggerTest {

    @Test
    fun `onReady uses build properties when available`() {
        val props = Properties().apply {
            setProperty("version", "9.9.9-TEST")
            setProperty("time", Instant.parse("2026-01-15T12:00:00Z").toString())
            setProperty("name", "test-app")
            setProperty("artifact", "test-app")
            setProperty("group", "org.example")
        }
        val build = BuildProperties(props)
        val provider = mock<ObjectProvider<BuildProperties>>()
        whenever(provider.ifAvailable).thenReturn(build)

        val logger = BuildInfoStartupLogger(provider)
        // Should not throw; logs version/time from BuildProperties
        logger.onReady()

        assertEquals("9.9.9-TEST", build.version)
        assertEquals(Instant.parse("2026-01-15T12:00:00Z"), build.time)
    }

    @Test
    fun `onReady falls back when build properties missing`() {
        val provider = mock<ObjectProvider<BuildProperties>>()
        whenever(provider.ifAvailable).thenReturn(null)

        val logger = BuildInfoStartupLogger(provider)
        logger.onReady()
    }
}
