package org.jrtech.platformmanagement.connectors.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConsumptionBlobPropertiesTest {

    @Test
    fun `resolved defaults to container root when nothing configured`() {
        assertThat(ConsumptionBlobProperties().resolvedBlobPrefixes())
            .containsExactly("")
    }

    @Test
    fun `resolved uses singular blobPrefix`() {
        assertThat(
            ConsumptionBlobProperties(blobPrefix = "capture").resolvedBlobPrefixes()
        ).containsExactly("capture")
    }

    @Test
    fun `resolved splits comma-separated singular prefix`() {
        assertThat(
            ConsumptionBlobProperties(blobPrefix = "eh-capture, manual/import").resolvedBlobPrefixes()
        ).containsExactly("eh-capture", "manual/import")
    }

    @Test
    fun `resolved uses blobPrefixes list`() {
        assertThat(
            ConsumptionBlobProperties(
                blobPrefixes = listOf("a", "b/c")
            ).resolvedBlobPrefixes()
        ).containsExactly("a", "b/c")
    }

    @Test
    fun `resolved merges list and singular and de-duplicates`() {
        assertThat(
            ConsumptionBlobProperties(
                blobPrefixes = listOf("a", "b"),
                blobPrefix = "b,c"
            ).resolvedBlobPrefixes()
        ).containsExactly("a", "b", "c")
    }

    @Test
    fun `resolved strips slashes and blanks`() {
        assertThat(
            ConsumptionBlobProperties(
                blobPrefixes = listOf("/nested/path/", "  x  ")
            ).resolvedBlobPrefixes()
        ).containsExactly("nested/path", "x")
    }

    @Test
    fun `explicit empty list entry includes container root with named prefixes`() {
        assertThat(
            ConsumptionBlobProperties(
                blobPrefixes = listOf("", "capture")
            ).resolvedBlobPrefixes()
        ).containsExactly("", "capture")
    }
}
