package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PriceForecastTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `PriceEntry round-trip serialization`() {
        val original = PriceForecastEntry(
            timestamp = Instant.fromEpochMilliseconds(1700000000000L),
            amount = Amount(0.25),
            currency = "EUR"
        )
        val bytes = cbor.encodeToByteArray(PriceForecastEntrySerializer, original)
        val decoded = cbor.decodeFromByteArray(PriceForecastEntrySerializer, bytes)
        assertEquals(original, decoded)
    }

    private val arbPriceForecastEntry = arbitrary {
        val millis = Arb.long(-62135596800000L..253402300799999L).bind()
        val timestamp = Instant.fromEpochMilliseconds(millis)
        val amount = Amount(Arb.double().bind())
        val currency = Arb.string(0..10).bind()
        PriceForecastEntry(timestamp, amount, currency)
    }

    @Test
    fun `PriceEntry round-trip serialization - property`() = runBlocking<Unit> {
        checkAll(arbPriceForecastEntry) { original ->
            val bytes = cbor.encodeToByteArray(PriceForecastEntrySerializer, original)
            val decoded = cbor.decodeFromByteArray(PriceForecastEntrySerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `PriceForecast round-trip serialization - property`() = runBlocking<Unit> {
        val arbPriceForecast = arbitrary {
            val size = Arb.int(0..10).bind()
            val entries = List(size) { arbPriceForecastEntry.bind() }
            PriceForecast(entries)
        }
        checkAll(arbPriceForecast) { original ->
            val bytes = cbor.encodeToByteArray(PriceForecastSerializer, original)
            val decoded = cbor.decodeFromByteArray(PriceForecastSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `PriceForecast round-trip serialization with multiple entries`() {
        val original = PriceForecast(
            entries = listOf(
                PriceForecastEntry(
                    timestamp = Instant.fromEpochMilliseconds(1700000000000L),
                    amount = Amount(0.25),
                    currency = "EUR"
                ),
                PriceForecastEntry(
                    timestamp = Instant.fromEpochMilliseconds(1700003600000L),
                    amount = Amount(0.30),
                    currency = "USD"
                ),
                PriceForecastEntry(
                    timestamp = Instant.parse("2025-02-01T12:00:00Z"),
                    amount = Amount(48.0),
                    currency = "SEK"
                )
            )
        )
        val bytes = cbor.encodeToByteArray(PriceForecastSerializer, original)
        val decoded = cbor.decodeFromByteArray(PriceForecastSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `PriceForecast round-trip serialization with empty entries`() {
        val original = PriceForecast(entries = emptyList())
        val bytes = cbor.encodeToByteArray(PriceForecastSerializer, original)
        val decoded = cbor.decodeFromByteArray(PriceForecastSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `Spec example - price forecast`() {
        // These bytes were not quite in the spec - they elided the full date value
        // This was produced by splicing a value in using the cbor.io test tools
        val bytes = "A118308183A10374323032352D30322D30315431323A30303A30305AA102F95150A1056353454B".hexToByteArray()
        val expected = PriceForecast(
            entries = listOf(
                PriceForecastEntry(Instant.parse("2025-02-01T12:00:00Z"), Amount(42.50), "SEK")
            )
        )

        val decoded = cbor.decodeFromByteArray(PriceForecastSerializer, bytes)
        assertEquals(expected, decoded)
    }
}
