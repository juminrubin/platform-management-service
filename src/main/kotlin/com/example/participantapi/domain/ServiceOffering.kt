package com.example.participantapi.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "service_offering")
class ServiceOffering(

    /** Business key, e.g. gpt-5.1-mini, az-whisper-stt */
    @field:Id
    @field:Column(nullable = false, updatable = false, length = 100)
    var id: String,

    @field:Column(nullable = false)
    var name: String,

    @field:Column(length = 1000)
    var description: String? = null,

    @field:Column(nullable = false, length = 64)
    var category: String,

    /** JSON configuration (deployment_endpoint, default_max_tpm, …) — VARCHAR(5000) NOT NULL */
    @field:Column(nullable = false, length = 5000)
    var config: String = "{}",

    @field:Column(nullable = false)
    var active: Boolean = true,

    @field:JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = UtcTimestamps.now(),

    @field:JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = UtcTimestamps.now()
) {
    @PrePersist
    fun onCreate() {
        val now = UtcTimestamps.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = UtcTimestamps.now()
    }
}
