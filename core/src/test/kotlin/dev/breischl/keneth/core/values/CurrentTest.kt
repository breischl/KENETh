package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurrentTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Current round-trip serialization`() {
        val original = Current(125.0)
        val bytes = cbor.encodeToByteArray(CurrentSerializer, original)
        val decoded = cbor.decodeFromByteArray(CurrentSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Current round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.double()) { value ->
            val original = Current(value)
            val bytes = cbor.encodeToByteArray(CurrentSerializer, original)
            val decoded = cbor.decodeFromByteArray(CurrentSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Spec example - Current 292_5A as float16`() {
        // A1 11 F9 5C 92 = map(1), key=17, float16(292.5)
        val specBytes = "A111F95C92".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(CurrentSerializer, specBytes)
        assertTrue(abs(decoded.amperes - 292.5) <= 0.1, "Expected ${decoded.amperes} to be within 0.1 of 292.5")
    }
}
