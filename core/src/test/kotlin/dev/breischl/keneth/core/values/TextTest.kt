package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Text round-trip serialization`() {
        val original = Text("Hello, World!")
        val bytes = cbor.encodeToByteArray(TextSerializer, original)
        val decoded = cbor.decodeFromByteArray(TextSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Text round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.string()) { value ->
            val original = Text(value)
            val bytes = cbor.encodeToByteArray(TextSerializer, original)
            val decoded = cbor.decodeFromByteArray(TextSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Spec example - Text`() {
        // A1 00 6C = map(1), key=0, text(12) for "Hello World!"
        val specBytes = "A1006C48656C6C6F20576F726C6421".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(TextSerializer, specBytes)
        assertEquals(Text("Hello World!"), decoded)
    }
}
