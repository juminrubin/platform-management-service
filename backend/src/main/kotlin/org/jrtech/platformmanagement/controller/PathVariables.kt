package org.jrtech.platformmanagement.controller

/**
 * Path ids may contain `/` (service offering business keys such as `Group1/Service1a`).
 * Spring `{*id}` captures the remainder including a leading slash.
 */
object PathVariables {
    fun fromRemaining(raw: String): String = raw.trim().removePrefix("/")
}
