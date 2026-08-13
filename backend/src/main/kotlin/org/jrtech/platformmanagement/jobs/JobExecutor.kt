package org.jrtech.platformmanagement.jobs

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide executor for background jobs (blob pipelines, future connector work).
 *
 * One [ExecutorService] per JVM. Callers construct their own job/pipeline objects
 * and [submit] them here — this type is not a Spring bean.
 *
 * Periodic ticks (Entra Graph refresh, datasource cache) stay on Spring's
 * [org.springframework.scheduling.TaskScheduler]. Use this pool for discrete
 * units of work that should run in parallel.
 */
object JobExecutor {

    const val DEFAULT_POOL_SIZE = 8

    private val lock = Any()
    private var executor: ExecutorService? = null
    private var poolSize: Int = 0

    fun isRunning(): Boolean {
        synchronized(lock) {
            val current = executor
            return current != null && !current.isShutdown
        }
    }

    fun poolSize(): Int = synchronized(lock) { poolSize }

    /**
     * Create the process-wide pool if it is not already running.
     * A later [poolSize] is ignored so the first initialization (usually Spring
     * `app.jobs.pool-size`) wins for the life of the JVM.
     */
    fun initialize(poolSize: Int = DEFAULT_POOL_SIZE): ExecutorService {
        val requested = poolSize.coerceAtLeast(1)
        synchronized(lock) {
            val current = executor
            if (current != null && !current.isShutdown) {
                return current
            }
            this.poolSize = requested
            val created = Executors.newFixedThreadPool(requested, JobThreadFactory())
            executor = created
            return created
        }
    }

    fun <T> submit(task: Callable<T>): Future<T> = initialize().submit(task)

    fun execute(task: Runnable) {
        initialize().execute(task)
    }

    /** Shut down the process-wide pool. Called when the JVM / Spring context stops. */
    fun shutdown(timeout: Long = 30, unit: TimeUnit = TimeUnit.SECONDS) {
        val current: ExecutorService
        synchronized(lock) {
            current = executor ?: return
            executor = null
            poolSize = 0
        }
        current.shutdown()
        if (!current.awaitTermination(timeout, unit)) {
            current.shutdownNow()
        }
    }

    private class JobThreadFactory : ThreadFactory {
        private val seq = AtomicInteger(1)
        override fun newThread(r: Runnable): Thread =
            Thread(r, "job-executor-${seq.getAndIncrement()}").apply {
                isDaemon = true
            }
    }
}
