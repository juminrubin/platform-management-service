package org.jrtech.platformmanagement.connectors.datasource

import org.jrtech.platformmanagement.cache.EntitlementCheckCache
import org.jrtech.platformmanagement.cache.EntitlementCheckCacheStatusResponse
import org.jrtech.platformmanagement.exception.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.TaskScheduler
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference

class DatasourceLoadingConnectorTest {

    private val cache = mock<EntitlementCheckCache>()
    private val taskScheduler = mock<TaskScheduler>()
    private val scheduledFuture = mock<ScheduledFuture<*>>()
    private val lastRunnable = AtomicReference<Runnable?>(null)

    @BeforeEach
    fun stubSchedulerAndCache() {
        whenever(taskScheduler.schedule(any(Runnable::class.java), any(Instant::class.java))).thenAnswer { inv ->
            lastRunnable.set(inv.getArgument(0))
            scheduledFuture
        }
        whenever(cache.status()).thenReturn(sampleStatus())
        whenever(cache.refresh(org.mockito.kotlin.any())).thenReturn(sampleStatus(loaded = true))
    }

    @Test
    fun `info reports disabled when connector disabled`() {
        val connector = connector(DatasourceLoadingProperties(enabled = false))
        val info = connector.info()
        assertThat(info.id).isEqualTo("datasource-loading")
        assertThat(info.enabled).isFalse()
        assertThat(info.detail).isEqualTo("disabled")
        assertThat(info.status).isEqualTo("DISABLED")
        assertThat(connector.health().status).isEqualTo("DISABLED")
        assertThat(connector.isEnabled()).isFalse()
    }

    @Test
    fun `start refreshes cache arms schedule and stop cancels`() {
        val connector = connector(
            DatasourceLoadingProperties(enabled = true, autoStart = false, refreshIntervalMs = 60_000L)
        )

        val started = connector.start("admin@x.com")
        assertThat(started.running).isTrue()
        assertThat(started.status).isEqualTo("RUNNING")
        assertThat(started.lastStartedBy).isEqualTo("admin@x.com")
        verify(cache).refresh(triggeredBy = "admin@x.com")
        verify(taskScheduler).schedule(any(Runnable::class.java), any(Instant::class.java))

        // second start is no-op
        connector.start("admin@x.com")
        verify(cache, times(1)).refresh(triggeredBy = "admin@x.com")

        val stopped = connector.stop("admin@x.com")
        assertThat(stopped.running).isFalse()
        assertThat(stopped.lastStoppedBy).isEqualTo("admin@x.com")
        verify(scheduledFuture).cancel(false)
    }

    @Test
    fun `start when disabled throws`() {
        val connector = connector(DatasourceLoadingProperties(enabled = false))
        assertThatThrownBy { connector.start("x") }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("disabled")
    }

    @Test
    fun `configure rejects low interval and accepts valid override`() {
        val connector = connector(
            DatasourceLoadingProperties(enabled = true, refreshIntervalMs = 3_600_000L)
        )
        assertThatThrownBy {
            connector.configure(mapOf("refreshIntervalMs" to 1000L))
        }.isInstanceOf(BadRequestException::class.java)

        assertThatThrownBy {
            connector.configure(mapOf("unknownKey" to "x"))
        }.isInstanceOf(BadRequestException::class.java)

        val cfg = connector.configure(mapOf("refreshIntervalMs" to 30_000L))
        assertThat(cfg["refreshIntervalMs"]).isEqualTo(30_000L)
        assertThat(cfg["refreshIntervalOverrideMs"]).isEqualTo(30_000L)
    }

    @Test
    fun `configure while running reschedules`() {
        val connector = connector(DatasourceLoadingProperties(enabled = true, refreshIntervalMs = 60_000L))
        connector.start("ops")
        connector.configure(mapOf("refreshIntervalMs" to 15_000L))
        // initial schedule + reschedule after configure
        verify(taskScheduler, times(2)).schedule(any(Runnable::class.java), any(Instant::class.java))
    }

    @Test
    fun `runLoadCycle failure sets lastError and rethrows`() {
        val connector = connector(DatasourceLoadingProperties(enabled = true))
        whenever(cache.refresh(org.mockito.kotlin.any())).thenThrow(RuntimeException("boom"))
        assertThatThrownBy { connector.runLoadCycle("tester") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("boom")
        assertThat(connector.info().lastError).isEqualTo("boom")
    }

    @Test
    fun `scheduled runnable refreshes when still running`() {
        val connector = connector(DatasourceLoadingProperties(enabled = true, refreshIntervalMs = 60_000L))
        connector.start("ops")
        val runnable = lastRunnable.get()
        assertThat(runnable).isNotNull
        whenever(cache.refresh(org.mockito.kotlin.any())).thenReturn(sampleStatus(loaded = true))
        runnable!!.run()
        verify(cache).refresh(triggeredBy = "SYSTEM-schedule")
    }

    @Test
    fun `autoStartIfConfigured starts when enabled`() {
        val connector = connector(DatasourceLoadingProperties(enabled = true, autoStart = true))
        connector.autoStartIfConfigured()
        verify(cache).refresh(triggeredBy = "SYSTEM")
        assertThat(connector.info().running).isTrue()
    }

    @Test
    fun `autoStartIfConfigured no-ops when disabled`() {
        val connector = connector(DatasourceLoadingProperties(enabled = false, autoStart = true))
        connector.autoStartIfConfigured()
        verify(cache, never()).refresh(org.mockito.kotlin.any())
    }

    @Test
    fun `info details for not loaded and error states`() {
        val connector = connector(DatasourceLoadingProperties(enabled = true))
        whenever(cache.status()).thenReturn(sampleStatus(loaded = false, lastError = null))
        assertThat(connector.info().detail).isEqualTo("not-loaded")

        whenever(cache.status()).thenReturn(
            sampleStatus(loaded = true, lastError = "x", refreshInProgress = true)
        )
        assertThat(connector.info().detail).isEqualTo("refresh-in-progress")
    }

    private fun connector(props: DatasourceLoadingProperties) =
        DatasourceLoadingConnector(props, cache, taskScheduler)

    private fun sampleStatus(
        loaded: Boolean = true,
        lastError: String? = null,
        refreshInProgress: Boolean = false
    ) = EntitlementCheckCacheStatusResponse(
        enabled = true,
        loaded = loaded,
        loadedAt = if (loaded) Instant.parse("2026-01-01T00:00:00Z") else null,
        entitlementsAsOf = null,
        lastRefreshBy = "test",
        lastRefreshStartedAt = null,
        lastRefreshFinishedAt = null,
        lastError = lastError,
        refreshInProgress = refreshInProgress,
        serviceCount = 1,
        callerCount = 1,
        entitlementCount = 1,
        scheduledRefreshEnabled = true,
        refreshIntervalMs = 3600000
    )
}
