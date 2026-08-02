package org.jrtech.platformmanagement.connectors.runtime

import org.jrtech.platformmanagement.exception.BadRequestException
import java.time.LocalDate

/**
 * Helpers for parsing connector configure payloads (JSON maps).
 */
object ConnectorConfigSupport {

    fun requireKnownKeys(updates: Map<String, Any?>, allowed: Set<String>) {
        val unknown = updates.keys.filter { it !in allowed }
        if (unknown.isNotEmpty()) {
            throw BadRequestException(
                "Unknown configuration key(s): ${unknown.sorted().joinToString(", ")}. " +
                    "Allowed: ${allowed.sorted().joinToString(", ")}"
            )
        }
    }

    fun optionalString(updates: Map<String, Any?>, key: String): String? {
        if (!updates.containsKey(key)) return null
        val v = updates[key] ?: return null
        return when (v) {
            is String -> v
            else -> v.toString()
        }
    }

    fun optionalBoolean(updates: Map<String, Any?>, key: String): Boolean? {
        if (!updates.containsKey(key)) return null
        return when (val v = updates[key]) {
            null -> null
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true) || v == "1"
            is Number -> v.toInt() != 0
            else -> throw BadRequestException("configuration.$key must be a boolean")
        }
    }

    fun optionalLong(updates: Map<String, Any?>, key: String): Long? {
        if (!updates.containsKey(key)) return null
        return when (val v = updates[key]) {
            null -> null
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
                ?: throw BadRequestException("configuration.$key must be a number")
            else -> throw BadRequestException("configuration.$key must be a number")
        }
    }

    fun optionalLocalDate(updates: Map<String, Any?>, key: String): LocalDate? {
        if (!updates.containsKey(key)) return null
        val v = updates[key] ?: return null
        val raw = when (v) {
            is String -> v
            else -> v.toString()
        }
        return try {
            LocalDate.parse(raw)
        } catch (ex: Exception) {
            throw BadRequestException("configuration.$key must be an ISO date (YYYY-MM-DD)")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun optionalStringList(updates: Map<String, Any?>, key: String): List<String>? {
        if (!updates.containsKey(key)) return null
        val v = updates[key] ?: return null
        return when (v) {
            is List<*> -> v.map { item ->
                when (item) {
                    null -> ""
                    is String -> item
                    else -> item.toString()
                }
            }
            is String -> v.split(',').map { it.trim() }
            else -> throw BadRequestException("configuration.$key must be a list of strings")
        }
    }
}
