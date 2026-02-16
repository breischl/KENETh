package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `Power round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.double()) { value ->
            val original = Power(value)
            val bytes = cbor.encodeToByteArray(PowerSerializer, original)
            val decoded = cbor.decodeFromByteArray(PowerSerializer, bytes)
            assertEquals(original, decoded)
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
