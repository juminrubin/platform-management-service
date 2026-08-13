package org.jrtech.platformmanagement.jobs

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Process-wide job pool (`app.jobs`).
 *
 * Shared by blob pipelines and any other discrete background work that should
 * not create its own executor.
 */
@ConfigurationProperties(prefix = "app.jobs")
data class JobExecutorProperties(
    /** Worker threads in [JobExecutor]. Default 8. */
    val poolSize: Int = JobExecutor.DEFAULT_POOL_SIZE
)
