package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `Currency round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(Arb.string()) { value ->
            val original = Currency(value)
            val bytes = cbor.encodeToByteArray(CurrencySerializer, original)
            val decoded = cbor.decodeFromByteArray(CurrencySerializer, bytes)
            assertEquals(original, decoded)
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
