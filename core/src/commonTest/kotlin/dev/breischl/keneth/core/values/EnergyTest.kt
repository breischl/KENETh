package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class EnergyTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Energy round-trip serialization`() {
        val original = Energy(75000.0)
        val bytes = cbor.encodeToByteArray(EnergySerializer, original)
        val decoded = cbor.decodeFromByteArray(EnergySerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Energy round-trip serialization - property`() = runTest {
        checkAll(Arb.double()) { value ->
            val original = Energy(value)
            val bytes = cbor.encodeToByteArray(EnergySerializer, original)
            val decoded = cbor.decodeFromByteArray(EnergySerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Energy deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x13)
        val wrongKeyBytes = "A11918991902E8".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(EnergySerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Energy deserialization fails with string value instead of number`() {
        // Map with correct key (0x13) but string value instead of number
        val stringValueBytes = "A11363666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(EnergySerializer, stringValueBytes)
        }
    }

    @Test
    fun `Spec example - Energy 12420029Wh as uint32`() {
        // A1 13 1A 00 BD 83 BD = map(1), key=19, uint32(12420029)
        val specBytes = "A1131A00BD83BD".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(EnergySerializer, specBytes)
        assertEquals(Energy(12420029.0), decoded)
    }
}
