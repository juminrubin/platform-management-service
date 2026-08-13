package org.jrtech.platformmanagement.connectors.consumption.blob.claim

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BlobFileClaimStoreTest {

    private val store = InMemoryBlobFileClaimStore()

    @Test
    fun `first claim wins and second is held or already succeeded`() {
        val first = store.tryClaim(
            inputContainer = "in",
            inputBlob = "a.avro",
            inputEtag = "etag-1",
            inputLength = 10,
            outputBlob = "a.parquet",
            owner = "pod-a",
            lease = Duration.ofMinutes(15)
        )
        assertThat(first).isInstanceOf(ClaimOutcome.Acquired::class.java)

        val second = store.tryClaim(
            inputContainer = "in",
            inputBlob = "a.avro",
            inputEtag = "etag-1",
            inputLength = 10,
            outputBlob = "a.parquet",
            owner = "pod-b",
            lease = Duration.ofMinutes(15)
        )
        assertThat(second).isInstanceOf(ClaimOutcome.HeldByOther::class.java)
    }

    @Test
    fun `seal then second claim is already succeeded`() {
        val acquired = store.tryClaim(
            "in", "a.avro", "etag-1", 10, "a.parquet", "pod-a", Duration.ofMinutes(15)
        ) as ClaimOutcome.Acquired
        assertThat(
            store.sealSucceeded("in", "a.avro", acquired.claim.generation, "pod-a", "a.parquet", 3)
        ).isTrue()
        val again = store.tryClaim(
            "in", "a.avro", "etag-1", 10, "a.parquet", "pod-b", Duration.ofMinutes(15)
        )
        assertThat(again).isInstanceOf(ClaimOutcome.AlreadySucceeded::class.java)
    }

    @Test
    fun `expired running claim can be stolen`() {
        val first = store.tryClaim(
            "in", "a.avro", "etag-1", 10, "a.parquet", "pod-a", Duration.ofSeconds(-1)
        ) as ClaimOutcome.Acquired
        val stolen = store.tryClaim(
            "in", "a.avro", "etag-1", 10, "a.parquet", "pod-b", Duration.ofMinutes(15)
        )
        assertThat(stolen).isInstanceOf(ClaimOutcome.Acquired::class.java)
        assertThat((stolen as ClaimOutcome.Acquired).claim.owner).isEqualTo("pod-b")
        assertThat(stolen.claim.generation).isEqualTo(first.claim.generation + 1)
        assertThat(
            store.sealSucceeded("in", "a.avro", first.claim.generation, "pod-a", "a.parquet", 1)
        ).isFalse()
    }

    @Test
    fun `etag change after success allows a new claim`() {
        val first = store.tryClaim(
            "in", "a.avro", "etag-1", 10, "a.parquet", "pod-a", Duration.ofMinutes(15)
        ) as ClaimOutcome.Acquired
        store.sealSucceeded("in", "a.avro", first.claim.generation, "pod-a", "a.parquet", 1)
        val next = store.tryClaim(
            "in", "a.avro", "etag-2", 11, "a.parquet", "pod-b", Duration.ofMinutes(15)
        )
        assertThat(next).isInstanceOf(ClaimOutcome.Acquired::class.java)
    }

    @Test
    fun `concurrent tryClaim yields a single owner`() {
        val wins = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) { i ->
            pool.submit {
                start.await()
                when (
                    store.tryClaim(
                        "in", "race.avro", "e", 1, "race.parquet", "pod-$i", Duration.ofMinutes(5)
                    )
                ) {
                    is ClaimOutcome.Acquired -> wins.incrementAndGet()
                    else -> Unit
                }
                done.countDown()
            }
        }
        start.countDown()
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
        pool.shutdownNow()
        assertThat(wins.get()).isEqualTo(1)
    }

    @Test
    fun `row key encodes slash paths`() {
        assertThat(BlobFileClaimKeys.rowKey("eh-capture/2024/07/01/10_00_00.avro"))
            .doesNotContain("/")
            .contains("eh-capture")
        assertThat(BlobFileClaimKeys.partitionKey("")).isEqualTo("_")
        assertThat(BlobFileClaimKeys.partitionKey("avro-in")).isEqualTo("avro-in")
    }
}
