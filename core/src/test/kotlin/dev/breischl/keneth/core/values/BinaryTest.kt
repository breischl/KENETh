package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertContentEquals

class BinaryTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Binary round-trip serialization`() {
        val original = Binary(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val bytes = cbor.encodeToByteArray(BinarySerializer, original)
        val decoded = cbor.decodeFromByteArray(BinarySerializer, bytes)
        assertContentEquals(original.bytes, decoded.bytes)
    }

    @Test
    fun `Binary round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.byteArray(Arb.int(0..1024), Arb.byte())) { value ->
            val original = Binary(value)
            val bytes = cbor.encodeToByteArray(BinarySerializer, original)
            val decoded = cbor.decodeFromByteArray(BinarySerializer, bytes)
            assertContentEquals(original.bytes, decoded.bytes)
        }
    }

    @Test
    fun `Spec example - Binary data`() {
        // A1 04 44 01 02 03 04 = map(1), key=4, bytes(4)
        val specBytes = "A1044401020304".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(BinarySerializer, specBytes)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), decoded.bytes)
    }
}
