package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VoltageTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Voltage round-trip serialization`() {
        val original = Voltage(824.5)
        val bytes = cbor.encodeToByteArray(VoltageSerializer, original)
        val decoded = cbor.decodeFromByteArray(VoltageSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Voltage round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.double()) { value ->
            val original = Voltage(value)
            val bytes = cbor.encodeToByteArray(VoltageSerializer, original)
            val decoded = cbor.decodeFromByteArray(VoltageSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Voltage example serialization`() {
        // Bytes taken from example in spec enetf-ep-messages.md, section 3.1
        val expectedBytes = "A1101902E8".hexToByteArray()
        val expectedValue = Voltage(744.0)

        val encoded = cbor.encodeToByteArray(VoltageSerializer, expectedValue)
        print(encoded.toHexString(HexFormat.UpperCase))

        val decoded = cbor.decodeFromByteArray(VoltageSerializer, expectedBytes)

        assertEquals(expectedValue, decoded)
    }

    @Test
    fun `Voltage deserialization fails with missing type ID key`() {
        // Map with wrong key (0x99 instead of 0x10)
        val wrongKeyBytes = "A11918991902E8".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(VoltageSerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Voltage deserialization fails with string value instead of number`() {
        // Map with correct key (0x10) but string value instead of number
        val stringValueBytes = "A11063666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(VoltageSerializer, stringValueBytes)
        }
    }

    @Test
    fun `Spec example - Voltage 744V as int`() {
        // A1 10 19 02 E8 = map(1), key=16, uint16(744)
        val specBytes = "A1101902E8".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(VoltageSerializer, specBytes)
        assertEquals(Voltage(744.0), decoded)
    }
}
