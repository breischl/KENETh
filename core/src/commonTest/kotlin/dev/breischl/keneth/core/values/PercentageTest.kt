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

class PercentageTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Percentage round-trip serialization`() {
        val original = Percentage(85.5)
        val bytes = cbor.encodeToByteArray(PercentageSerializer, original)
        val decoded = cbor.decodeFromByteArray(PercentageSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Percentage round-trip serialization - property`() = runTest {
        checkAll(Arb.double()) { value ->
            val original = Percentage(value)
            val bytes = cbor.encodeToByteArray(PercentageSerializer, original)
            val decoded = cbor.decodeFromByteArray(PercentageSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Percentage deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x14)
        val wrongKeyBytes = "A11918991902E8".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(PercentageSerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Percentage deserialization fails with string value instead of number`() {
        // Map with correct key (0x14) but string value instead of number
        val stringValueBytes = "A11463666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(PercentageSerializer, stringValueBytes)
        }
    }

    @Test
    fun `Spec example - Percentage 99_9999 as float64`() {
        // A1 14 FB 40 58 FF FE 5C 91 D1 4E = map(1), key=20, float64(99.9999)
        val specBytes = "A114FB4058FFFE5C91D14E".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(PercentageSerializer, specBytes)
        assertTrue(
            abs(decoded.percent - 99.9999) <= 0.0001,
            "Expected ${decoded.percent} to be within 0.0001 of 99.9999"
        )
    }
}
