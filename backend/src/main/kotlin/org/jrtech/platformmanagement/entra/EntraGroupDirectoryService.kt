package org.jrtech.platformmanagement.entra

import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.domain.UtcTimestamps
import org.jrtech.platformmanagement.dto.EntraDirectorySnapshotResponse
import org.jrtech.platformmanagement.dto.EntraGroupMemberResponse
import org.jrtech.platformmanagement.dto.EntraGroupWithMembersResponse
import org.jrtech.platformmanagement.logging.logger
import org.jrtech.platformmanagement.security.EntraGroupPermissionScopeTable
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads members of Entra groups whose display name starts with
 * [EntraDirectoryProperties.groupNamePrefix] (default `Platform-System-`)
 * and stores the mapping in a **thread-safe** [ConcurrentHashMap]:
 *
 * ```
 * groupId → EntraGroupWithMembers (group metadata + member list)
 * ```
 *
 * Also maintains reverse indexes for **human authorization**:
 * - email / UPN / mail → permission scopes (via [EntraGroupPermissionScopeTable])
 * - member object id → permission scopes
 * - group object id → permission scopes
 *
 * The entire map is replaced atomically on each successful Microsoft Graph refresh.
 * Readers always see a consistent snapshot.
 *
 * Lifecycle (who calls [refresh]):
 * - Connector **start** / **auto-start** (immediate load for auth warm-up)
 * - Connector schedule while **running** (fixed delay)
 * - Manual `POST /api/v1/entra/groups/refresh` (one-shot; does not change running state)
 *
 * When [EntraDirectoryProperties.enabled] is false, the map stays empty and list
 * APIs return an empty view without failing startup.
 */
@Service
class EntraGroupDirectoryService(
    private val properties: EntraDirectoryProperties,
    private val graphClientProvider: ObjectProvider<MicrosoftGraphClient>
) {
    private val log = logger()

    /**
     * Thread-safe group → members mapping.
     * Held in an [AtomicReference] so each Graph refresh installs a new map instance
     * without mutating the map currently being read by other threads.
     */
    private val groupMembersByGroupId =
        AtomicReference<ConcurrentHashMap<String, EntraGroupWithMembers>>(ConcurrentHashMap())

    /**
     * Reverse index for human auth: identity key → app role values.
     * Keys: `upn:<email>`, `mail:<email>`, `oid:<objectId>` (lowercase).
     */
    private val appRolesByMemberKey =
        AtomicReference<ConcurrentHashMap<String, Set<String>>>(ConcurrentHashMap())

    /** group object id → app role (from display name mapping). */
    private val appRoleByGroupId =
        AtomicReference<ConcurrentHashMap<String, String>>(ConcurrentHashMap())

    /** identity key → platform group display names. */
    private val groupNamesByMemberKey =
        AtomicReference<ConcurrentHashMap<String, Set<String>>>(ConcurrentHashMap())

    /** Timestamp of the last successful map replacement (UTC). */
    private val loadedAt = AtomicReference<Instant?>(null)

    /** Who triggered the last refresh attempt (JWT principal, SYSTEM-startup, SYSTEM-schedule, …). */
    private val lastRefreshBy = AtomicReference<String?>(null)

    private val lastRefreshStartedAt = AtomicReference<Instant?>(null)
    private val lastRefreshFinishedAt = AtomicReference<Instant?>(null)
    private val lastError = AtomicReference<String?>(null)
    private val refreshInProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Serializes Graph loads so concurrent refresh calls do not interleave writes. */
    private val refreshLock = Any()

    /**
     * Unmodifiable live view of the current group→members map.
     * Safe for concurrent reads; map identity changes after each successful refresh.
     */
    fun groupMembersMap(): Map<String, EntraGroupWithMembers> =
        Collections.unmodifiableMap(groupMembersByGroupId.get())

    fun membersOf(groupId: String): List<EntraDirectoryMember> =
        groupMembersByGroupId.get()[groupId.trim()]?.members.orEmpty()

    fun findGroup(groupId: String): EntraGroupWithMembers? =
        groupMembersByGroupId.get()[groupId.trim()]

    fun lastLoadedAt(): Instant? = loadedAt.get()

    fun lastRefreshBy(): String? = lastRefreshBy.get()

    fun lastRefreshStartedAt(): Instant? = lastRefreshStartedAt.get()

    fun lastRefreshFinishedAt(): Instant? = lastRefreshFinishedAt.get()

    fun lastError(): String? = lastError.get()

    fun isRefreshInProgress(): Boolean = refreshInProgress.get()

    fun hasGraphClient(): Boolean = graphClientProvider.getIfAvailable() != null

    /**
     * App roles for a human identified by email(s) and/or Entra object id(s),
     * based on membership in cached `Platform-System-*` groups.
     */
    fun appRolesForHumanIdentity(
        emails: Collection<String> = emptyList(),
        objectIds: Collection<String> = emptyList()
    ): List<String> {
        val roles = linkedSetOf<String>()
        val index = appRolesByMemberKey.get()
        for (key in identityKeys(emails, objectIds)) {
            index[key]?.forEach { roles += it }
        }
        return roles.toList()
    }

    /**
     * Platform group display names for a human (from membership index).
     */
    fun groupDisplayNamesForHumanIdentity(
        emails: Collection<String> = emptyList(),
        objectIds: Collection<String> = emptyList()
    ): List<String> {
        val names = linkedSetOf<String>()
        val index = groupNamesByMemberKey.get()
        for (key in identityKeys(emails, objectIds)) {
            index[key]?.forEach { names += it }
        }
        return names.toList()
    }

    /**
     * App roles for Entra group object ids (JWT `groups` claim), using cached display names.
     */
    fun appRolesForGroupIds(groupIds: Collection<String>): List<String> {
        val roles = linkedSetOf<String>()
        val byGroup = appRoleByGroupId.get()
        val groups = groupMembersByGroupId.get()
        for (rawId in groupIds) {
            val id = rawId.trim()
            if (id.isEmpty()) continue
            byGroup[id]?.let { roles += it }
            // Fallback if reverse index empty but group map is populated
            if (!byGroup.containsKey(id)) {
                groups[id]?.let { gwm ->
                    val scope = EntraGroupPermissionScopeTable.scopeForGroupDisplayName(gwm.group.displayName)
                    if (scope.isNotEmpty()) roles += scope
                }
            }
        }
        return roles.toList()
    }

    fun snapshot(): EntraDirectorySnapshot {
        val map = groupMembersByGroupId.get()
        val groups = map.values
            .sortedBy { it.group.displayName.lowercase() }
            .toList()
        return EntraDirectorySnapshot(
            enabled = properties.enabled,
            groupNamePrefix = properties.groupNamePrefix,
            loadedAt = loadedAt.get(),
            groups = groups
        )
    }

    fun snapshotResponse(): EntraDirectorySnapshotResponse {
        val snap = snapshot()
        return EntraDirectorySnapshotResponse(
            enabled = snap.enabled,
            groupNamePrefix = snap.groupNamePrefix,
            loadedAt = snap.loadedAt,
            groupCount = snap.groupCount,
            memberCount = snap.memberCount,
            groups = snap.groups.map { gwm ->
                EntraGroupWithMembersResponse(
                    id = gwm.group.id,
                    displayName = gwm.group.displayName,
                    description = gwm.group.description,
                    members = gwm.members.map { m ->
                        EntraGroupMemberResponse(
                            id = m.id,
                            displayName = m.displayName,
                            userPrincipalName = m.userPrincipalName,
                            mail = m.mail,
                            odataType = m.odataType
                        )
                    }
                )
            }
        )
    }

    /**
     * All unique members across cached groups (by member object id).
     */
    fun allMembers(): List<EntraDirectoryMember> {
        val byId = linkedMapOf<String, EntraDirectoryMember>()
        for (gwm in groupMembersByGroupId.get().values) {
            for (member in gwm.members) {
                byId.putIfAbsent(member.id, member)
            }
        }
        return byId.values.toList()
    }

    /**
     * Reloads group→member mappings from Microsoft Graph into a new
     * [ConcurrentHashMap], then swaps it in atomically (including reverse indexes).
     *
     * @param triggeredBy audit label for connector/run monitoring
     * @return snapshot built from the installed map
     */
    fun refresh(triggeredBy: String = "SYSTEM"): EntraDirectorySnapshot {
        synchronized(refreshLock) {
            val started = UtcTimestamps.now()
            lastRefreshBy.set(triggeredBy)
            lastRefreshStartedAt.set(started)
            refreshInProgress.set(true)
            try {
                if (!properties.enabled) {
                    installMaps(
                        groups = ConcurrentHashMap(),
                        rolesByMember = ConcurrentHashMap(),
                        roleByGroup = ConcurrentHashMap(),
                        namesByMember = ConcurrentHashMap(),
                        loaded = null
                    )
                    lastError.set(null)
                    lastRefreshFinishedAt.set(UtcTimestamps.now())
                    log.debug("Entra directory load skipped (app.entra-directory.enabled=false)")
                    return snapshot()
                }

                val client = graphClientProvider.getIfAvailable()
                if (client == null) {
                    val msg = "Entra directory enabled but MicrosoftGraphClient bean is missing"
                    log.warn(msg)
                    lastError.set(msg)
                    lastRefreshFinishedAt.set(UtcTimestamps.now())
                    return snapshot()
                }

                val prefix = properties.groupNamePrefix.trim().ifEmpty { "Platform-System-" }
                log.info(
                    "Loading Entra groups with displayName prefix '{}' into ConcurrentHashMap (by={})",
                    prefix,
                    triggeredBy
                )

                return try {
                    val groups = client.listGroupsByDisplayNamePrefix(prefix)
                        .filter { it.displayName.startsWith(prefix) }
                        .sortedBy { it.displayName.lowercase() }

                    val next = ConcurrentHashMap<String, EntraGroupWithMembers>(groups.size.coerceAtLeast(16))
                    for (group in groups) {
                        val members = try {
                            client.listGroupMembers(group.id, properties.includeTransitiveMembers)
                                .sortedWith(
                                    compareBy(
                                        { it.userPrincipalName?.lowercase() ?: "" },
                                        { it.displayName?.lowercase() ?: "" },
                                        { it.id }
                                    )
                                )
                        } catch (ex: Exception) {
                            log.error(
                                "Failed to list members for group id={} name={}",
                                group.id,
                                group.displayName,
                                ex
                            )
                            emptyList()
                        }
                        next[group.id] = EntraGroupWithMembers(
                            group = group,
                            members = members.toList()
                        )
                        log.info(
                            "Mapped Entra group '{}' ({}) → {} member(s)",
                            group.displayName,
                            group.id,
                            members.size
                        )
                    }

                    val indexes = buildReverseIndexes(next)
                    installMaps(
                        groups = next,
                        rolesByMember = indexes.rolesByMember,
                        roleByGroup = indexes.roleByGroup,
                        namesByMember = indexes.namesByMember,
                        loaded = UtcTimestamps.now()
                    )
                    lastError.set(null)
                    lastRefreshFinishedAt.set(UtcTimestamps.now())
                    val snap = snapshot()
                    log.info(
                        "Entra group→member ConcurrentHashMap refreshed by={}: {} group(s), {} member slot(s), " +
                            "unique members={}, member-role index keys={}",
                        triggeredBy,
                        snap.groupCount,
                        snap.memberCount,
                        allMembers().size,
                        indexes.rolesByMember.size
                    )
                    snap
                } catch (ex: Exception) {
                    lastError.set(ex.message ?: ex.javaClass.simpleName)
                    lastRefreshFinishedAt.set(UtcTimestamps.now())
                    log.error("Failed to load Entra groups with prefix '{}'; keeping previous map", prefix, ex)
                    throw ex
                }
            } finally {
                refreshInProgress.set(false)
            }
        }
    }

    /**
     * Install a pre-built group map (used by tests to simulate Graph without network).
     */
    fun replaceCacheForTesting(groups: Map<String, EntraGroupWithMembers>) {
        val map = ConcurrentHashMap<String, EntraGroupWithMembers>(groups)
        val indexes = buildReverseIndexes(map)
        installMaps(
            groups = map,
            rolesByMember = indexes.rolesByMember,
            roleByGroup = indexes.roleByGroup,
            namesByMember = indexes.namesByMember,
            loaded = UtcTimestamps.now()
        )
    }

    private data class ReverseIndexes(
        val rolesByMember: ConcurrentHashMap<String, Set<String>>,
        val roleByGroup: ConcurrentHashMap<String, String>,
        val namesByMember: ConcurrentHashMap<String, Set<String>>
    )

    private fun buildReverseIndexes(
        groups: ConcurrentHashMap<String, EntraGroupWithMembers>
    ): ReverseIndexes {
        val rolesByMember = ConcurrentHashMap<String, MutableSet<String>>()
        val namesByMember = ConcurrentHashMap<String, MutableSet<String>>()
        val roleByGroup = ConcurrentHashMap<String, String>()

        fun addRole(key: String, role: String) {
            rolesByMember.compute(key) { _, existing ->
                val set = existing ?: linkedSetOf()
                set += role
                set
            }
        }

        fun addGroupName(key: String, groupName: String) {
            namesByMember.compute(key) { _, existing ->
                val set = existing ?: linkedSetOf()
                set += groupName
                set
            }
        }

        for ((groupId, gwm) in groups) {
            // Static table only: Entra group display name → permission scope (one per group)
            val scope = EntraGroupPermissionScopeTable.scopeForGroupDisplayName(gwm.group.displayName)
            if (scope.isNotEmpty()) {
                roleByGroup[groupId] = scope
            }
            val groupName = gwm.group.displayName
            for (member in gwm.members) {
                val keys = memberIdentityKeys(member)
                for (key in keys) {
                    addGroupName(key, groupName)
                    if (scope.isNotEmpty()) {
                        addRole(key, scope)
                    }
                }
            }
        }

        // Freeze as immutable sets for readers
        val frozenRoles = ConcurrentHashMap<String, Set<String>>(rolesByMember.size)
        rolesByMember.forEach { (k, v) -> frozenRoles[k] = v.toSet() }
        val frozenNames = ConcurrentHashMap<String, Set<String>>(namesByMember.size)
        namesByMember.forEach { (k, v) -> frozenNames[k] = v.toSet() }

        return ReverseIndexes(
            rolesByMember = frozenRoles,
            roleByGroup = roleByGroup,
            namesByMember = frozenNames
        )
    }

    private fun memberIdentityKeys(member: EntraDirectoryMember): Set<String> {
        val keys = linkedSetOf<String>()
        member.id.trim().takeIf { it.isNotEmpty() }?.let { keys += "oid:${it.lowercase()}" }
        member.userPrincipalName?.trim()?.takeIf { it.isNotEmpty() }?.let { upn ->
            val lower = upn.lowercase()
            keys += "upn:$lower"
            keys += "mail:$lower"
        }
        member.mail?.trim()?.takeIf { it.isNotEmpty() }?.let { mail ->
            val lower = mail.lowercase()
            keys += "mail:$lower"
            keys += "upn:$lower"
        }
        return keys
    }

    private fun identityKeys(emails: Collection<String>, objectIds: Collection<String>): Set<String> {
        val keys = linkedSetOf<String>()
        for (email in emails) {
            val lower = email.trim().lowercase()
            if (lower.isEmpty()) continue
            keys += "upn:$lower"
            keys += "mail:$lower"
        }
        for (oid in objectIds) {
            val lower = oid.trim().lowercase()
            if (lower.isNotEmpty()) keys += "oid:$lower"
        }
        return keys
    }

    private fun installMaps(
        groups: ConcurrentHashMap<String, EntraGroupWithMembers>,
        rolesByMember: ConcurrentHashMap<String, Set<String>>,
        roleByGroup: ConcurrentHashMap<String, String>,
        namesByMember: ConcurrentHashMap<String, Set<String>>,
        loaded: Instant?
    ) {
        groupMembersByGroupId.set(groups)
        appRolesByMemberKey.set(rolesByMember)
        appRoleByGroupId.set(roleByGroup)
        groupNamesByMemberKey.set(namesByMember)
        loadedAt.set(loaded)
    }
}
