package org.jrtech.platformmanagement.domain

/** Helpers for status enums stored as strings in Azure Table / JSON. */
object StatusParsing {
    fun participantStatus(raw: String?, default: ParticipantStatus = ParticipantStatus.ACTIVE): ParticipantStatus =
        parse(raw, default)

    fun entitlementStatus(raw: String?, default: EntitlementStatus = EntitlementStatus.PENDING): EntitlementStatus =
        parse(raw, default)

    fun callerRegistrationStatus(
        raw: String?,
        default: CallerRegistrationStatus = CallerRegistrationStatus.ACTIVE
    ): CallerRegistrationStatus = parse(raw, default)

    private inline fun <reified E : Enum<E>> parse(raw: String?, default: E): E {
        val value = raw?.trim()?.uppercase().orEmpty()
        if (value.isEmpty()) return default
        return try {
            java.lang.Enum.valueOf(E::class.java, value)
        } catch (_: IllegalArgumentException) {
            default
        }
    }
}
