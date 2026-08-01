package org.jrtech.platformmanagement.domain

/**
 * Well-known principals for `created_by` / `updated_by` audit columns.
 *
 * Values are applied **only in business logic (services)**. Entities and the DB schema
 * do not default them — callers must set them explicitly on create/update.
 */
object AuditActors {
    /** Platform / automated actor when no authenticated principal is used. */
    const val SYSTEM: String = "SYSTEM"
}
