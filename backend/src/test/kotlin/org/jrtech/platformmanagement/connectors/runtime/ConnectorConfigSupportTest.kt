package org.jrtech.platformmanagement.connectors.runtime

import org.jrtech.platformmanagement.exception.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ConnectorConfigSupportTest {

    @Test
    fun `requireKnownKeys accepts only allowed keys`() {
        ConnectorConfigSupport.requireKnownKeys(
            mapOf("a" to 1, "b" to 2),
            setOf("a", "b", "c")
        )
    }

    @Test
    fun `requireKnownKeys rejects unknown keys`() {
        assertThatThrownBy {
            ConnectorConfigSupport.requireKnownKeys(
                mapOf("z" to true, "a" to 1),
                setOf("a")
            )
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("Unknown configuration key(s): z")
            .hasMessageContaining("Allowed: a")
    }

    @Test
    fun `optionalString handles missing null string and non-string`() {
        assertThat(ConnectorConfigSupport.optionalString(emptyMap(), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalString(mapOf("k" to null), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalString(mapOf("k" to "hello"), "k")).isEqualTo("hello")
        assertThat(ConnectorConfigSupport.optionalString(mapOf("k" to 42), "k")).isEqualTo("42")
    }

    @Test
    fun `optionalBoolean parses boolean string number and rejects invalid`() {
        assertThat(ConnectorConfigSupport.optionalBoolean(emptyMap(), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalBoolean(mapOf("k" to null), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalBoolean(mapOf("k" to true), "k")).isTrue()
        assertThat(ConnectorConfigSupport.optionalBoolean(mapOf("k" to false), "k")).isFalse()
        assertThat(ConnectorConfigSupport.optionalBoolean(mapOf("k" to "TRUE"), "k")).isTrue()
        assertThat(ConnectorConfigSupport.optionalBoolean(mapOf("k" to "1"), "k")).isTrue()
        assertThat(ConnectorConfigSupport.optionalBoolean(mapOf("k" to "no"), "k")).isFalse()
        assertThat(ConnectorConfigSupport.optionalBoolean(mapOf("k" to 2), "k")).isTrue()
        assertThat(ConnectorConfigSupport.optionalBoolean(mapOf("k" to 0), "k")).isFalse()
        assertThatThrownBy {
            ConnectorConfigSupport.optionalBoolean(mapOf("k" to listOf(1)), "k")
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("must be a boolean")
    }

    @Test
    fun `optionalLong parses number and string and rejects invalid`() {
        assertThat(ConnectorConfigSupport.optionalLong(emptyMap(), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalLong(mapOf("k" to null), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalLong(mapOf("k" to 900_000), "k")).isEqualTo(900_000L)
        assertThat(ConnectorConfigSupport.optionalLong(mapOf("k" to "60"), "k")).isEqualTo(60L)
        assertThatThrownBy {
            ConnectorConfigSupport.optionalLong(mapOf("k" to "nope"), "k")
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("must be a number")
        assertThatThrownBy {
            ConnectorConfigSupport.optionalLong(mapOf("k" to true), "k")
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("must be a number")
    }

    @Test
    fun `optionalLocalDate parses string and non-string and rejects invalid`() {
        assertThat(ConnectorConfigSupport.optionalLocalDate(emptyMap(), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalLocalDate(mapOf("k" to null), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalLocalDate(mapOf("k" to "2024-07-01"), "k"))
            .isEqualTo(LocalDate.of(2024, 7, 1))
        assertThat(
            ConnectorConfigSupport.optionalLocalDate(
                mapOf("k" to LocalDate.of(2024, 1, 2)),
                "k"
            )
        ).isEqualTo(LocalDate.of(2024, 1, 2))
        assertThatThrownBy {
            ConnectorConfigSupport.optionalLocalDate(mapOf("k" to "not-a-date"), "k")
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("ISO date")
    }

    @Test
    fun `optionalStringList parses list comma string and rejects invalid`() {
        assertThat(ConnectorConfigSupport.optionalStringList(emptyMap(), "k")).isNull()
        assertThat(ConnectorConfigSupport.optionalStringList(mapOf("k" to null), "k")).isNull()
        assertThat(
            ConnectorConfigSupport.optionalStringList(
                mapOf("k" to listOf("a", null, 3)),
                "k"
            )
        ).containsExactly("a", "", "3")
        assertThat(
            ConnectorConfigSupport.optionalStringList(mapOf("k" to "eh-capture, manual"), "k")
        ).containsExactly("eh-capture", "manual")
        assertThatThrownBy {
            ConnectorConfigSupport.optionalStringList(mapOf("k" to 12), "k")
        }.isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("list of strings")
    }
}
