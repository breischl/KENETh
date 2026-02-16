package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

class ResistanceTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Resistance round-trip serialization`() {
        val original = Resistance(1000.0)
        val bytes = cbor.encodeToByteArray(ResistanceSerializer, original)
        val decoded = cbor.decodeFromByteArray(ResistanceSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Resistance round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.double()) { value ->
            val original = Resistance(value)
            val bytes = cbor.encodeToByteArray(ResistanceSerializer, original)
            val decoded = cbor.decodeFromByteArray(ResistanceSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Spec example - Resistance 1000 ohms`() {
        // A1 15 19 03 E8 = map(1), key=0x15, uint16(1000)
        // Extracted from the IsolationState binary example in spec section 3.1.5
        val specBytes = "A1151903E8".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(ResistanceSerializer, specBytes)
        assertEquals(Resistance(1000.0), decoded)
    }
}
