package org.jrtech.platformmanagement.config

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import java.time.Instant
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenApiConfigTest {

    @Test
    fun `openAPI stamps version and build time from BuildProperties`() {
        val props = Properties().apply {
            setProperty("version", "2.3.4")
            setProperty("time", Instant.parse("2026-03-01T08:30:00Z").toString())
            setProperty("name", "Platform Management Service")
            setProperty("artifact", "platform-management-service")
            setProperty("group", "org.jrtech")
        }
        val build = BuildProperties(props)
        val provider = mock<ObjectProvider<BuildProperties>>()
        whenever(provider.ifAvailable).thenReturn(build)

        val openApi = OpenApiConfig(provider).openAPI()

        assertEquals("2.3.4", openApi.info.version)
        assertTrue(openApi.info.description.contains("2.3.4"))
        assertTrue(openApi.info.description.contains("2026-03-01T08:30:00Z"))
    }

    @Test
    fun `openAPI falls back when BuildProperties missing`() {
        val provider = mock<ObjectProvider<BuildProperties>>()
        whenever(provider.ifAvailable).thenReturn(null)

        val openApi = OpenApiConfig(provider).openAPI()

        assertEquals(OpenApiConfig.FALLBACK_VERSION, openApi.info.version)
        assertTrue(openApi.info.description.contains(BuildInfoStartupLogger.UNKNOWN))
    }
}
