package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

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
    fun `Text round-trip serialization - property`() = runTest {
        checkAll(Arb.string()) { value ->
            val original = Text(value)
            val bytes = cbor.encodeToByteArray(TextSerializer, original)
            val decoded = cbor.decodeFromByteArray(TextSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Text deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x00), string value "foo"
        val wrongKeyBytes = "A119189963666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(TextSerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Text deserialization fails with integer value instead of string`() {
        // Map with correct key (0x00) but integer value instead of string
        val intValueBytes = "A10001".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(TextSerializer, intValueBytes)
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
