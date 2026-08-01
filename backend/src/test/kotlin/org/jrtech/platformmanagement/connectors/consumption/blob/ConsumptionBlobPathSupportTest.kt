package org.jrtech.platformmanagement.connectors.consumption.blob

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ConsumptionBlobPathSupportTest {

    @Test
    fun `dayDirectoryPrefix without root`() {
        val day = LocalDate.of(2024, 7, 1)
        assertThat(ConsumptionBlobPathSupport.dayDirectoryPrefix("", day))
            .isEqualTo("2024/07/01/")
        assertThat(ConsumptionBlobPathSupport.dayDirectoryPrefix("  /  ", day))
            .isEqualTo("2024/07/01/")
    }

    @Test
    fun `dayDirectoryPrefix with root strips slashes`() {
        val day = LocalDate.of(2024, 1, 15)
        assertThat(ConsumptionBlobPathSupport.dayDirectoryPrefix("capture", day))
            .isEqualTo("capture/2024/01/15/")
        assertThat(ConsumptionBlobPathSupport.dayDirectoryPrefix("/capture/", day))
            .isEqualTo("capture/2024/01/15/")
        assertThat(ConsumptionBlobPathSupport.dayDirectoryPrefix("  nested/path  ", day))
            .isEqualTo("nested/path/2024/01/15/")
    }

    @Test
    fun `daysInclusive single day`() {
        val d = LocalDate.of(2024, 3, 10)
        assertThat(ConsumptionBlobPathSupport.daysInclusive(d, d)).containsExactly(d)
    }

    @Test
    fun `daysInclusive multi day`() {
        val start = LocalDate.of(2024, 3, 10)
        val end = LocalDate.of(2024, 3, 12)
        assertThat(ConsumptionBlobPathSupport.daysInclusive(start, end))
            .containsExactly(
                LocalDate.of(2024, 3, 10),
                LocalDate.of(2024, 3, 11),
                LocalDate.of(2024, 3, 12)
            )
    }

    @Test
    fun `daysInclusive rejects inverted range`() {
        assertThatThrownBy {
            ConsumptionBlobPathSupport.daysInclusive(
                LocalDate.of(2024, 3, 12),
                LocalDate.of(2024, 3, 10)
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `dayDirectoryPrefixes multiplies days by root prefixes`() {
        val days = listOf(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 2))
        assertThat(
            ConsumptionBlobPathSupport.dayDirectoryPrefixes(listOf("a", "b"), days)
        ).containsExactly(
            "a/2024/07/01/",
            "b/2024/07/01/",
            "a/2024/07/02/",
            "b/2024/07/02/"
        )
    }

    @Test
    fun `dayDirectoryPrefixes empty roots means container root only`() {
        val day = LocalDate.of(2024, 1, 1)
        assertThat(ConsumptionBlobPathSupport.dayDirectoryPrefixes(emptyList(), listOf(day)))
            .containsExactly("2024/01/01/")
    }
}
