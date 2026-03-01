package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import net.orandja.obor.codec.Cbor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CurrentTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Current round-trip serialization`() {
        val original = Current(125.0)
        val bytes = cbor.encodeToByteArray(CurrentSerializer, original)
        val decoded = cbor.decodeFromByteArray(CurrentSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Current round-trip serialization - property`() = runTest {
        checkAll(Arb.double()) { value ->
            val original = Current(value)
            val bytes = cbor.encodeToByteArray(CurrentSerializer, original)
            val decoded = cbor.decodeFromByteArray(CurrentSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Current deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x11)
        val wrongKeyBytes = "A11918991902E8".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(CurrentSerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Current deserialization fails with string value instead of number`() {
        // Map with correct key (0x11) but string value instead of number
        val stringValueBytes = "A11163666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(CurrentSerializer, stringValueBytes)
        }
    }

    @Test
    fun `Spec example - Current 292_5A as float16`() {
        // A1 11 F9 5C 92 = map(1), key=17, float16(292.5)
        val specBytes = "A111F95C92".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(CurrentSerializer, specBytes)
        assertTrue(abs(decoded.amperes - 292.5) <= 0.1, "Expected ${decoded.amperes} to be within 0.1 of 292.5")
    }
}
