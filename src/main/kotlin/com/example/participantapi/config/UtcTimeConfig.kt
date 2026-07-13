package com.example.participantapi.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Forces the JVM default timezone to UTC so any zone-sensitive APIs (logging,
 * JDBC drivers, formatters) do not fall back to the host OS zone.
 *
 * Persistence and JSON also pin UTC via application.yml:
 * - spring.jpa.properties.hibernate.jdbc.time_zone=UTC
 * - spring.jackson.time-zone=UTC
 * - Instant fields use @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
 */
@Configuration
class UtcTimeConfig {

    @PostConstruct
    fun forceJvmDefaultToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC))
    }
}
