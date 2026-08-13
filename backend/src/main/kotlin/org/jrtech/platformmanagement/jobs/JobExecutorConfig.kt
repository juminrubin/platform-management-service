package org.jrtech.platformmanagement.jobs

import org.jrtech.platformmanagement.logging.logger
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Starts and stops the JVM-wide [JobExecutor] with the Spring process.
 * [JobExecutor] itself remains a singleton object, not a bean.
 */
@Configuration
@EnableConfigurationProperties(JobExecutorProperties::class)
class JobExecutorConfig(
    private val properties: JobExecutorProperties
) {
    private val log = logger()

    @PostConstruct
    fun start() {
        JobExecutor.initialize(properties.poolSize)
        log.info("JobExecutor started (poolSize={})", JobExecutor.poolSize())
    }

    @PreDestroy
    fun stop() {
        JobExecutor.shutdown()
        log.info("JobExecutor stopped")
    }
}
