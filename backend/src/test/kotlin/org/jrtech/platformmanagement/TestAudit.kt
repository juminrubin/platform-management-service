package org.jrtech.platformmanagement

import org.jrtech.platformmanagement.domain.AuditActors

/**
 * Shared audit actor values for unit/persistence tests that construct entities
 * outside of service create/update paths.
 */
object TestAudit {
    val BY: String = AuditActors.SYSTEM
}
