package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

class DurationTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Duration round-trip serialization`() {
        val original = Duration(3600000L)
        val bytes = cbor.encodeToByteArray(DurationSerializer, original)
        val decoded = cbor.decodeFromByteArray(DurationSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Duration round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.long()) { value ->
            val original = Duration(value)
            val bytes = cbor.encodeToByteArray(DurationSerializer, original)
            val decoded = cbor.decodeFromByteArray(DurationSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Spec example - Duration 1000ms`() {
        // A1 06 19 03 E8 = map(1), key=6, uint16(1000)
        val specBytes = "A1061903E8".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(DurationSerializer, specBytes)
        assertEquals(Duration(1000L), decoded)
    }
}
