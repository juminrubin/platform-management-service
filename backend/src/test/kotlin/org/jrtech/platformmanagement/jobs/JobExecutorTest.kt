package org.jrtech.platformmanagement.jobs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ResourceLock("job-executor")
class JobExecutorTest {

    @AfterEach
    fun tearDown() {
        JobExecutor.shutdown(1, TimeUnit.SECONDS)
    }

    @Test
    fun `first initialize creates a process-wide pool that later calls reuse`() {
        val first = JobExecutor.initialize(3)
        val second = JobExecutor.initialize(8)
        assertThat(second).isSameAs(first)
        assertThat(JobExecutor.isRunning()).isTrue()
        assertThat(JobExecutor.poolSize()).isEqualTo(3)
    }

    @Test
    fun `submit runs work on the shared pool`() {
        JobExecutor.initialize(2)
        val seen = AtomicInteger()
        val done = CountDownLatch(2)
        val f1 = JobExecutor.submit {
            seen.incrementAndGet()
            done.countDown()
            "a"
        }
        val f2 = JobExecutor.submit {
            seen.incrementAndGet()
            done.countDown()
            "b"
        }
        assertThat(f1.get(5, TimeUnit.SECONDS)).isEqualTo("a")
        assertThat(f2.get(5, TimeUnit.SECONDS)).isEqualTo("b")
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(seen.get()).isEqualTo(2)
        assertThat(JobExecutor.isRunning()).isTrue()
    }

    @Test
    fun `shutdown stops the pool and next initialize creates a new one`() {
        val original = JobExecutor.initialize(2)
        JobExecutor.shutdown(1, TimeUnit.SECONDS)
        assertThat(JobExecutor.isRunning()).isFalse()
        assertThat(JobExecutor.poolSize()).isEqualTo(0)
        val recreated = JobExecutor.initialize(2)
        assertThat(recreated).isNotSameAs(original)
        assertThat(JobExecutor.isRunning()).isTrue()
    }
}
