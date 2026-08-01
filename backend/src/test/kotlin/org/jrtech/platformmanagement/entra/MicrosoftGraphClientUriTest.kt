package org.jrtech.platformmanagement.entra

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import org.jrtech.platformmanagement.config.EntraDirectoryProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

/**
 * Ensures OData query params are not double-encoded.
 * Pre-encoding with URLEncoder + RestClient.uri(String) produced Graph HTTP 400:
 * "character '%' is not valid … id%2CdisplayName…" (i.e. select still had %2C after decode).
 */
class MicrosoftGraphClientUriTest {

    private val client = MicrosoftGraphClientImpl(
        properties = EntraDirectoryProperties(
            enabled = true,
            graphBaseUrl = "https://graph.microsoft.com/v1.0"
        ),
        credential = TokenCredential { _: TokenRequestContext ->
            Mono.just(AccessToken("test-token", OffsetDateTime.now().plusHours(1)))
        }
    )

    @Test
    fun `groups URI keeps select commas readable and never double-encodes`() {
        val filter = "startswith(displayName,'Platform-System-')"
        val select = "id,displayName,description"
        val uri = client.buildGroupsUri(filter = filter, select = select)
        val raw = uri.toASCIIString()

        assertThat(raw).startsWith("https://graph.microsoft.com/v1.0/groups?")
        // Never double-encode (Graph then sees literal "%2C" and returns 400)
        assertThat(raw).doesNotContain("%252C")
        assertThat(raw).doesNotContain("id%252CdisplayName")

        // Select value must decode to plain comma-separated field names
        val selectValue = queryParamValue(raw, "\$select")
            ?: queryParamValue(raw, "%24select")
        assertThat(selectValue).isNotNull()
        val decodedSelect = java.net.URLDecoder.decode(selectValue, Charsets.UTF_8)
        assertThat(decodedSelect).isEqualTo("id,displayName,description")

        val filterValue = queryParamValue(raw, "\$filter")
            ?: queryParamValue(raw, "%24filter")
        assertThat(filterValue).isNotNull()
        val decodedFilter = java.net.URLDecoder.decode(filterValue, Charsets.UTF_8)
        assertThat(decodedFilter).isEqualTo(filter)
    }

    @Test
    fun `members URI path uses user cast and does not double-encode select`() {
        val uri = client.buildGroupMembersUri(
            groupId = "11111111-2222-3333-4444-555555555555",
            segment = "members/microsoft.graph.user",
            select = "id,displayName,userPrincipalName,mail"
        )
        val raw = uri.toASCIIString()
        assertThat(raw).contains(
            "/groups/11111111-2222-3333-4444-555555555555/members/microsoft.graph.user?"
        )
        assertThat(raw).doesNotContain("%252C")

        val selectValue = queryParamValue(raw, "\$select")
            ?: queryParamValue(raw, "%24select")
        assertThat(selectValue).isNotNull()
        assertThat(java.net.URLDecoder.decode(selectValue, Charsets.UTF_8))
            .isEqualTo("id,displayName,userPrincipalName,mail")
    }

    @Test
    fun `user URI does not double-encode select`() {
        val uri = client.buildUserUri("e768c9e0-6558-45ae-8980-70374bfe7bf9")
        val raw = uri.toASCIIString()
        assertThat(raw).contains("/users/e768c9e0-6558-45ae-8980-70374bfe7bf9?")
        assertThat(raw).doesNotContain("%252C")
        val selectValue = queryParamValue(raw, "\$select")
            ?: queryParamValue(raw, "%24select")
        assertThat(java.net.URLDecoder.decode(selectValue, Charsets.UTF_8))
            .isEqualTo("id,displayName,userPrincipalName,mail")
    }

    private fun queryParamValue(url: String, name: String): String? {
        val query = url.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
    }
}
