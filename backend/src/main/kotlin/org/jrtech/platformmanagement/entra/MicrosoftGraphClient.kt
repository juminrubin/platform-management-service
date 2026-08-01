package org.jrtech.platformmanagement.entra

/**
 * Minimal Microsoft Graph surface used to load Entra groups and members.
 */
interface MicrosoftGraphClient {

    /**
     * Security / Microsoft 365 groups whose [displayName] starts with [displayNamePrefix].
     */
    fun listGroupsByDisplayNamePrefix(displayNamePrefix: String): List<EntraGroup>

    /**
     * Direct or transitive members of [groupId] depending on [transitive].
     */
    fun listGroupMembers(groupId: String, transitive: Boolean): List<EntraDirectoryMember>
}
