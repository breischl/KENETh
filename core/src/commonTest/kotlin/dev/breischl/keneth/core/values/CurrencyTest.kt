package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class CurrencyTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `Currency round-trip serialization`() {
        val original = Currency("USD")
        val bytes = cbor.encodeToByteArray(CurrencySerializer, original)
        val decoded = cbor.decodeFromByteArray(CurrencySerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Currency round-trip serialization - property`() = runTest {
        checkAll(Arb.string()) { value ->
            val original = Currency(value)
            val bytes = cbor.encodeToByteArray(CurrencySerializer, original)
            val decoded = cbor.decodeFromByteArray(CurrencySerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Currency deserialization fails with missing type ID key`() {
        // Map with wrong key (0x1899 instead of 0x05), string value "foo"
        val wrongKeyBytes = "A119189963666F6F".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(CurrencySerializer, wrongKeyBytes)
        }
    }

    @Test
    fun `Currency deserialization fails with integer value instead of string`() {
        // Map with correct key (0x05) but integer value instead of string
        val intValueBytes = "A10501".hexToByteArray()
        assertFailsWith<IllegalStateException> {
            cbor.decodeFromByteArray(CurrencySerializer, intValueBytes)
        }
    }

    @Test
    fun `Spec example - Currency SEK`() {
        // A1 05 63 53 45 4B = map(1), key=5, text(3) "SEK"
        val specBytes = "A1056353454B".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(CurrencySerializer, specBytes)
        assertEquals(Currency("SEK"), decoded)
    }
}
