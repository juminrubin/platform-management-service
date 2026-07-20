package org.jrtech.platformmanagement.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kotlin-friendly Logback/SLF4J logger accessor.
 *
 * Usage:
 * ```
 * private val log = logger()
 * log.info("Created participant id={}", id)
 * ```
 */
inline fun <reified T : Any> T.logger(): Logger =
    LoggerFactory.getLogger(T::class.java)
