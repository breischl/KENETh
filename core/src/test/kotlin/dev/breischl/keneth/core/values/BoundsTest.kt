package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundsTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Bounds Voltage round-trip serialization`() {
        val original = Bounds(Voltage(200.0), Voltage(920.0))
        val bytes = cbor.encodeToByteArray(BoundsSerializer.voltage, original)
        val decoded = cbor.decodeFromByteArray(BoundsSerializer.voltage, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Bounds Current round-trip serialization`() {
        val original = Bounds(Current(0.0), Current(500.0))
        val bytes = cbor.encodeToByteArray(BoundsSerializer.current, original)
        val decoded = cbor.decodeFromByteArray(BoundsSerializer.current, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Bounds Voltage round-trip serialization - property`() = runBlocking<Unit> {
        val arbVoltage = Arb.double().map { Voltage(it) }
        checkAll(arbVoltage, arbVoltage) { min, max ->
            val original = Bounds(min, max)
            val bytes = cbor.encodeToByteArray(BoundsSerializer.voltage, original)
            val decoded = cbor.decodeFromByteArray(BoundsSerializer.voltage, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Bounds Current round-trip serialization - property`() = runBlocking<Unit> {
        val arbCurrent = Arb.double().map { Current(it) }
        checkAll(arbCurrent, arbCurrent) { min, max ->
            val original = Bounds(min, max)
            val bytes = cbor.encodeToByteArray(BoundsSerializer.current, original)
            val decoded = cbor.decodeFromByteArray(BoundsSerializer.current, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Spec example - Bounds voltage 200V to 1000V`() {
        // From spec section 3.1.1:
        // A1 18 20 82 A1 10 18 C8 A1 10 19 03 E8
        // = {0x20: [{0x10: 200}, {0x10: 1000}]}
        val specBytes = "A1182082A11018C8A1101903E8".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(BoundsSerializer.voltage, specBytes)
        assertEquals(Bounds(Voltage(200.0), Voltage(1000.0)), decoded)
    }

    @Test
    fun `Bounds negative voltage serialization`() {
        val bytes = "A1182082A11038C7A1103903E7".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(BoundsSerializer.voltage, bytes)
        assertEquals(Voltage(-200.0), decoded.min)
        assertEquals(Voltage(-1000.0), decoded.max)
    }
}
