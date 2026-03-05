package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class PowerTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Power round-trip serialization`() {
        val original = Power(50000.0)
        val bytes = cbor.encodeToByteArray(PowerSerializer, original)
        val decoded = cbor.decodeFromByteArray(PowerSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Power round-trip serialization - property`() = runTest {
        checkAll(Arb.double()) { value ->
            val original = Power(value)
            val bytes = cbor.encodeToByteArray(PowerSerializer, original)
            val decoded = cbor.decodeFromByteArray(PowerSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Power deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x12)
        val wrongKeyBytes = "A11918991902E8".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(PowerSerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Power deserialization fails with string value instead of number`() {
        // Map with correct key (0x12) but string value instead of number
        val stringValueBytes = "A11263666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(PowerSerializer, stringValueBytes)
        }
    }

    @Test
    fun `Spec example - Power 50000W as float32`() {
        // A1 12 FA 47 43 50 00 = map(1), key=18, float32(50000.0)
        val specBytes = "A112FA47435000".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(PowerSerializer, specBytes)
        assertEquals(Power(50000.0), decoded)
    }
}
