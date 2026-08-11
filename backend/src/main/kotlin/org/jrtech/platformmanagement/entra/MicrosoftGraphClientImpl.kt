package org.jrtech.platformmanagement.entra

import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.jrtech.platformmanagement.logging.logger
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import java.net.URI

/**
 * Microsoft Graph HTTP client using [TokenCredential]
 * (UAMI, service principal, or SAMI via AzureCredentialFactory).
 *
 * Uses advanced query headers for `startswith` group filters
 * (`ConsistencyLevel: eventual` + `$count=true`).
 *
 * Group members are loaded via OData cast to `microsoft.graph.user` so profile fields
 * (`displayName`, `userPrincipalName`, `mail`) resolve with `User.Read.All`. When the
 * cast response still lacks those fields, each user is hydrated with `GET /users/{id}`.
 *
 * Query parameters are encoded **once** via [UriComponentsBuilder]. Do not pre-encode values
 * with [java.net.URLEncoder] before passing them to RestClient — that double-encodes commas
 * (`%2C` → `%252C`) and Graph returns HTTP 400 on `$select` / `$filter`.
 */
class MicrosoftGraphClientImpl(
    private val properties: EntraDirectoryProperties,
    private val credential: TokenCredential,
    private val restClient: RestClient = RestClient.create()
) : MicrosoftGraphClient {

    private val log = logger()

    override fun listGroupsByDisplayNamePrefix(displayNamePrefix: String): List<EntraGroup> {
        val prefix = displayNamePrefix.trim()
        require(prefix.isNotEmpty()) { "displayNamePrefix must not be blank" }

        // OData string literal: escape single quotes by doubling them
        val escaped = prefix.replace("'", "''")
        val filter = "startswith(displayName,'$escaped')"
        val select = "id,displayName,description"

        val groups = mutableListOf<EntraGroup>()
        var nextUri: URI? = buildGroupsUri(filter = filter, select = select)
        while (nextUri != null) {
            val page = getJsonRequired(nextUri, advancedQuery = true)
            val values = page.get("value")
            if (values != null && values.isArray) {
                values.forEach { node ->
                    val id = textOrEmpty(node, "id")
                    val name = textOrEmpty(node, "displayName")
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        groups += EntraGroup(
                            id = id,
                            displayName = name,
                            description = textOrNull(node, "description")
                        )
                    }
                }
            }
            nextUri = textOrNull(page, "@odata.nextLink")?.let { URI.create(it) }
        }
        log.debug("Graph listed {} group(s) with prefix '{}'", groups.size, prefix)
        return groups
    }

    override fun listGroupMembers(groupId: String, transitive: Boolean): List<EntraDirectoryMember> {
        val id = groupId.trim()
        require(id.isNotEmpty()) { "groupId must not be blank" }

        // Cast to user so profile properties are in scope (mixed directoryObject + $select
        // often returns only id / @odata.type even with User.Read.All).
        val segment = if (transitive) {
            "transitiveMembers/microsoft.graph.user"
        } else {
            "members/microsoft.graph.user"
        }

        val members = mutableListOf<EntraDirectoryMember>()
        var nextUri: URI? = buildGroupMembersUri(groupId = id, segment = segment, select = USER_SELECT)
        while (nextUri != null) {
            val page = getJsonRequired(nextUri, advancedQuery = false)
            val values = page.get("value")
            if (values != null && values.isArray) {
                values.forEach { node ->
                    val memberId = textOrEmpty(node, "id")
                    if (memberId.isNotEmpty()) {
                        members += EntraDirectoryMember(
                            id = memberId,
                            displayName = textOrNull(node, "displayName"),
                            userPrincipalName = textOrNull(node, "userPrincipalName"),
                            mail = textOrNull(node, "mail"),
                            odataType = textOrNull(node, "@odata.type")
                                ?: "#microsoft.graph.user"
                        )
                    }
                }
            }
            nextUri = textOrNull(page, "@odata.nextLink")?.let { URI.create(it) }
        }

        var hydrateCount = 0
        val hydrated = members.map { member ->
            if (needsUserHydration(member)) {
                val resolved = getUser(member.id)
                if (resolved != null && !needsUserHydration(resolved)) {
                    hydrateCount++
                    resolved
                } else {
                    resolved ?: member
                }
            } else {
                member
            }
        }

        log.debug(
            "Graph listed {} user member(s) for group {} (transitive={}, hydrated={})",
            hydrated.size,
            id,
            transitive,
            hydrateCount
        )
        return hydrated
    }

    /**
     * Builds `/groups?$count&$filter&$select&$top` with single-pass encoding.
     * Visible for unit tests that assert no double-encoding of commas.
     */
    internal fun buildGroupsUri(filter: String, select: String): URI =
        UriComponentsBuilder
            .fromUriString(graphRoot())
            .pathSegment("groups")
            .queryParam("\$count", "true")
            .queryParam("\$filter", filter)
            .queryParam("\$select", select)
            .queryParam("\$top", "999")
            .encode()
            .build()
            .toUri()

    /**
     * Builds `/groups/{id}/{segment}?$select&$top`.
     * [segment] may be `members`, `transitiveMembers`, or
     * `members/microsoft.graph.user` / `transitiveMembers/microsoft.graph.user`.
     */
    internal fun buildGroupMembersUri(groupId: String, segment: String, select: String): URI {
        val parts = segment.split('/').filter { it.isNotEmpty() }
        val builder = UriComponentsBuilder
            .fromUriString(graphRoot())
            .pathSegment("groups", groupId, *parts.toTypedArray())
        return builder
            .queryParam("\$select", select)
            .queryParam("\$top", "999")
            .encode()
            .build()
            .toUri()
    }

    internal fun buildUserUri(userId: String, select: String = USER_SELECT): URI =
        UriComponentsBuilder
            .fromUriString(graphRoot())
            .pathSegment("users", userId.trim())
            .queryParam("\$select", select)
            .encode()
            .build()
            .toUri()

    /**
     * Loads a single user profile. Returns null if not found or Graph rejects the call.
     */
    internal fun getUser(userId: String): EntraDirectoryMember? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        return try {
            val node = getJsonOptional(buildUserUri(id), advancedQuery = false)
                ?: return null
            val resolvedId = textOrEmpty(node, "id").ifEmpty { id }
            EntraDirectoryMember(
                id = resolvedId,
                displayName = textOrNull(node, "displayName"),
                userPrincipalName = textOrNull(node, "userPrincipalName"),
                mail = textOrNull(node, "mail"),
                odataType = "#microsoft.graph.user"
            )
        } catch (ex: Exception) {
            log.warn("Graph GET /users/{} failed: {}", id, ex.message)
            null
        }
    }

    private fun needsUserHydration(member: EntraDirectoryMember): Boolean =
        member.displayName.isNullOrBlank() &&
            member.userPrincipalName.isNullOrBlank() &&
            member.mail.isNullOrBlank()

    private fun graphRoot(): String = properties.graphBaseUrl.trimEnd('/')

    private fun getJsonRequired(uri: URI, advancedQuery: Boolean): JsonNode =
        getJsonOptional(uri, advancedQuery)
            ?: throw IllegalStateException("Empty Graph response for $uri")

    private fun getJsonOptional(uri: URI, advancedQuery: Boolean): JsonNode? {
        val token = acquireToken()
        return try {
            // Pass a java.net.URI so RestClient does not re-encode query values.
            var spec = restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            if (advancedQuery) {
                spec = spec.header("ConsistencyLevel", "eventual")
            }
            spec.retrieve().body(JsonNode::class.java)
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND || ex.statusCode.value() == 404) {
                log.debug("Graph resource not found url={}", uri)
                return null
            }
            log.error(
                "Microsoft Graph request failed status={} url={} body={}",
                ex.statusCode.value(),
                uri,
                ex.responseBodyAsString.take(500)
            )
            throw IllegalStateException(
                "Microsoft Graph request failed with HTTP ${ex.statusCode.value()}: ${ex.message}",
                ex
            )
        }
    }

    private fun acquireToken(): String {
        val context = TokenRequestContext().addScopes("https://graph.microsoft.com/.default")
        val accessToken = credential.getToken(context).block()
            ?: throw IllegalStateException("Failed to acquire Microsoft Graph access token")
        return accessToken.token
    }

    private fun textOrEmpty(node: JsonNode, field: String): String {
        val child = node.get(field) ?: return ""
        if (child.isNull) return ""
        return child.asString("").trim()
    }

    private fun textOrNull(node: JsonNode, field: String): String? {
        val child = node.get(field) ?: return null
        if (child.isNull) return null
        val text = child.asString("").trim()
        return text.takeIf { it.isNotEmpty() }
    }

    companion object {
        internal const val USER_SELECT: String = "id,displayName,userPrincipalName,mail"
    }
}
