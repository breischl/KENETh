package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class DurationTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Duration round-trip serialization`() {
        val original = Duration(3600000L)
        val bytes = cbor.encodeToByteArray(DurationSerializer, original)
        val decoded = cbor.decodeFromByteArray(DurationSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Duration round-trip serialization - property`() = runTest {
        checkAll(Arb.long()) { value ->
            val original = Duration(value)
            val bytes = cbor.encodeToByteArray(DurationSerializer, original)
            val decoded = cbor.decodeFromByteArray(DurationSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Duration deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x06), integer value
        val wrongKeyBytes = "A11918991903E8".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(DurationSerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Duration deserialization fails with string value instead of integer`() {
        // Map with correct key (0x06) but string value instead of integer
        val stringValueBytes = "A10663666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(DurationSerializer, stringValueBytes)
        }
    }

    @Test
    fun `Spec example - Duration 1000ms`() {
        // A1 06 19 03 E8 = map(1), key=6, uint16(1000)
        val specBytes = "A1061903E8".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(DurationSerializer, specBytes)
        assertEquals(Duration(1000L), decoded)
    }
}
