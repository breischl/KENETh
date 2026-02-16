package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

class AmountTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Amount round-trip serialization`() {
        val original = Amount(99.99)
        val bytes = cbor.encodeToByteArray(AmountSerializer, original)
        val decoded = cbor.decodeFromByteArray(AmountSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Amount round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.double()) { value ->
            val original = Amount(value)
            val bytes = cbor.encodeToByteArray(AmountSerializer, original)
            val decoded = cbor.decodeFromByteArray(AmountSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Spec example - Amount as float16`() {
        // A1 02 F9 52 00 = map(1), key=2, float16(48.0)
        val specBytes = "A102F95200".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(AmountSerializer, specBytes)
        assertEquals(Amount(48.0), decoded)
    }
}
