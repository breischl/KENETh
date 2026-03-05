package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class AmountTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Amount round-trip serialization`() {
        val original = Amount(99.99)
        val bytes = cbor.encodeToByteArray(AmountSerializer, original)
        val decoded = cbor.decodeFromByteArray(AmountSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Amount round-trip serialization - property`() = runTest {
        checkAll(Arb.double()) { value ->
            val original = Amount(value)
            val bytes = cbor.encodeToByteArray(AmountSerializer, original)
            val decoded = cbor.decodeFromByteArray(AmountSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Amount deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x02)
        val wrongKeyBytes = "A11918991902E8".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(AmountSerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Amount deserialization fails with string value instead of number`() {
        // Map with correct key (0x02) but string value instead of number
        val stringValueBytes = "A10263666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(AmountSerializer, stringValueBytes)
        }
    }

    @Test
    fun `Spec example - Amount as float16`() {
        // A1 02 F9 52 00 = map(1), key=2, float16(48.0)
        val specBytes = "A102F95200".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(AmountSerializer, specBytes)
        assertEquals(Amount(48.0), decoded)
    }
}
