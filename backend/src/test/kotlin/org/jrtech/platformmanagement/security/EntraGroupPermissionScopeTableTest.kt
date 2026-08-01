package org.jrtech.platformmanagement.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EntraGroupPermissionScopeTableTest {

    @Test
    fun `static table maps Platform-System groups to permission scopes`() {
        assertThat(EntraGroupPermissionScopeTable.BY_GROUP_DISPLAY_NAME).containsOnlyKeys(
            "Platform-System-Maintainer",
            "Platform-System-Reader",
            "Platform-System-Entitlement-Reader",
            "Platform-System-Consumption-Registrator"
        )

        assertThat(EntraGroupPermissionScopeTable.scopeForGroupDisplayName("Platform-System-Maintainer"))
            .isEqualTo(AppRoles.SYSTEM_MAINTAINER)
        assertThat(EntraGroupPermissionScopeTable.scopeForGroupDisplayName("Platform-System-Reader"))
            .isEqualTo(AppRoles.SYSTEM_READER)
        assertThat(EntraGroupPermissionScopeTable.scopeForGroupDisplayName("Platform-System-Entitlement-Reader"))
            .isEqualTo(AppRoles.ENTITLEMENT_READER)
        assertThat(EntraGroupPermissionScopeTable.scopeForGroupDisplayName("Platform-System-Consumption-Registrator"))
            .isEqualTo(AppRoles.CONSUMPTION_REGISTRATOR)
    }

    @Test
    fun `lookup is case-insensitive and unknown groups return empty`() {
        assertThat(EntraGroupPermissionScopeTable.scopeForGroupDisplayName("platform-system-maintainer"))
            .isEqualTo(AppRoles.SYSTEM_MAINTAINER)
        assertThat(EntraGroupPermissionScopeTable.scopeForGroupDisplayName("Other-Admins")).isEmpty()
        assertThat(EntraGroupPermissionScopeTable.scopeForGroupDisplayName("")).isEmpty()
        assertThat(EntraGroupPermissionScopeTable.primaryScopeForGroupDisplayName("Unknown")).isEmpty()
    }

    @Test
    fun `scopesForGroupDisplayNames unions unique scopes`() {
        assertThat(
            EntraGroupPermissionScopeTable.scopesForGroupDisplayNames(
                listOf(
                    "Platform-System-Reader",
                    "Platform-System-Maintainer",
                    "Platform-System-Reader",
                    "Not-A-Group"
                )
            )
        ).containsExactly(AppRoles.SYSTEM_READER, AppRoles.SYSTEM_MAINTAINER)
    }
}
