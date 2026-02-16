package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `Energy round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.double()) { value ->
            val original = Energy(value)
            val bytes = cbor.encodeToByteArray(EnergySerializer, original)
            val decoded = cbor.decodeFromByteArray(EnergySerializer, bytes)
            assertEquals(original, decoded)
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
