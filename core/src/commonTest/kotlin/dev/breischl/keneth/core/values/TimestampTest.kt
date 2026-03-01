package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.time.Instant
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class TimestampTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Timestamp round-trip serialization`() {
        val original = Timestamp(Instant.fromEpochMilliseconds(1700000000000L))
        val bytes = cbor.encodeToByteArray(TimestampSerializer, original)
        val decoded = cbor.decodeFromByteArray(TimestampSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Timestamp round-trip serialization - property`() = runTest {
        checkAll(Arb.long()) { millis ->
            val original = Timestamp(Instant.fromEpochMilliseconds(millis))
            val bytes = cbor.encodeToByteArray(TimestampSerializer, original)
            val decoded = cbor.decodeFromByteArray(TimestampSerializer, bytes)
            assertEquals(original, decoded)
        }
    }
}
