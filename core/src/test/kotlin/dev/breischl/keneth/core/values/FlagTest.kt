package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

class FlagTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Flag round-trip serialization`() {
        val originalTrue = Flag(true)
        val bytesTrue = cbor.encodeToByteArray(FlagSerializer, originalTrue)
        val decodedTrue = cbor.decodeFromByteArray(FlagSerializer, bytesTrue)
        assertEquals(originalTrue, decodedTrue)

        val originalFalse = Flag(false)
        val bytesFalse = cbor.encodeToByteArray(FlagSerializer, originalFalse)
        val decodedFalse = cbor.decodeFromByteArray(FlagSerializer, bytesFalse)
        assertEquals(originalFalse, decodedFalse)
    }

    @Test
    fun `Flag round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.boolean()) { value ->
            val original = Flag(value)
            val bytes = cbor.encodeToByteArray(FlagSerializer, original)
            val decoded = cbor.decodeFromByteArray(FlagSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Spec example - Flag false`() {
        // A1 01 F4 = map(1), key=1, false
        val specBytes = "A101F4".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(FlagSerializer, specBytes)
        assertEquals(Flag(false), decoded)
    }

    @Test
    fun `Spec example - Flag true`() {
        // A1 01 F5 = map(1), key=1, true
        val specBytes = "A101F5".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(FlagSerializer, specBytes)
        assertEquals(Flag(true), decoded)
    }
}
