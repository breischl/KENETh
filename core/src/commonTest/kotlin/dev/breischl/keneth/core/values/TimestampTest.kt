package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.time.Instant
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `Timestamp deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x03), integer value
        val wrongKeyBytes = "A11918991903E8".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(TimestampSerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Timestamp deserialization fails with integer value instead of string`() {
        // Map with correct key (0x03) but integer value instead of ISO-8601 string
        val intValueBytes = "A10301".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(TimestampSerializer, intValueBytes)
        }
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
