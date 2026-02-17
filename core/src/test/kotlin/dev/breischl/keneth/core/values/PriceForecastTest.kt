package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.diagnostics.DiagnosticCollector
import dev.breischl.keneth.core.diagnostics.DiagnosticContext
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import net.orandja.obor.data.CborMap
import net.orandja.obor.data.CborObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    private fun buildValidEntryArray(
        timestamp: String = "2025-02-01T12:00:00Z",
        amount: Double = 42.50,
        currency: String = "SEK"
    ) = CborObject.buildArray {
        add(SerializerUtils.buildStringMap(Timestamp.TYPE_ID, timestamp))
        add(SerializerUtils.buildDoubleMap(Amount.TYPE_ID, amount))
        add(SerializerUtils.buildStringMap(Currency.TYPE_ID, currency))
    }

    @Test
    fun `deserialize skips non-array entry with warning`() {
        val entriesArray = CborObject.buildArray {
            add(buildValidEntryArray())
            add(CborObject.buildMap { }) // not an array
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(PriceForecast.TYPE_ID), entriesArray)
        }
        val bytes = cbor.encodeToByteArray(CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(PriceForecastSerializer, bytes)
        }

        assertEquals(1, decoded.entries.size)
        assertEquals(1, collector.diagnostics.size)
        assertEquals("INVALID_PRICE_ENTRY", collector.diagnostics[0].code)
        assertTrue(collector.diagnostics[0].message.contains("Expected CborArray"))
    }

    @Test
    fun `deserialize skips entry with wrong array size`() {
        val shortEntry = CborObject.buildArray {
            add(SerializerUtils.buildStringMap(Timestamp.TYPE_ID, "2025-02-01T12:00:00Z"))
            add(SerializerUtils.buildDoubleMap(Amount.TYPE_ID, 10.0))
            // missing currency element
        }
        val entriesArray = CborObject.buildArray {
            add(buildValidEntryArray())
            add(shortEntry)
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(PriceForecast.TYPE_ID), entriesArray)
        }
        val bytes = cbor.encodeToByteArray(CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(PriceForecastSerializer, bytes)
        }

        assertEquals(1, decoded.entries.size)
        assertEquals(1, collector.diagnostics.size)
        assertEquals("INVALID_PRICE_ENTRY", collector.diagnostics[0].code)
        assertTrue(collector.diagnostics[0].message.contains("2 elements"))
    }

    @Test
    fun `deserialize skips entry with missing timestamp`() {
        val badEntry = CborObject.buildArray {
            add(CborObject.buildMap {
                put(CborObject.positive(Amount.TYPE_ID), CborObject.value("not-a-timestamp"))
            })
            add(SerializerUtils.buildDoubleMap(Amount.TYPE_ID, 10.0))
            add(SerializerUtils.buildStringMap(Currency.TYPE_ID, "USD"))
        }
        val entriesArray = CborObject.buildArray {
            add(buildValidEntryArray())
            add(badEntry)
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(PriceForecast.TYPE_ID), entriesArray)
        }
        val bytes = cbor.encodeToByteArray(CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(PriceForecastSerializer, bytes)
        }

        assertEquals(1, decoded.entries.size)
        assertEquals(1, collector.diagnostics.size)
        assertEquals("INVALID_PRICE_ENTRY", collector.diagnostics[0].code)
        assertTrue(collector.diagnostics[0].message.contains("timestamp"))
    }

    @Test
    fun `deserialize skips entry with missing amount`() {
        val badEntry = CborObject.buildArray {
            add(SerializerUtils.buildStringMap(Timestamp.TYPE_ID, "2025-02-01T12:00:00Z"))
            add(CborObject.buildMap {
                put(CborObject.positive(Currency.TYPE_ID), CborObject.value(10.0))
            })
            add(SerializerUtils.buildStringMap(Currency.TYPE_ID, "USD"))
        }
        val entriesArray = CborObject.buildArray {
            add(buildValidEntryArray())
            add(badEntry)
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(PriceForecast.TYPE_ID), entriesArray)
        }
        val bytes = cbor.encodeToByteArray(CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(PriceForecastSerializer, bytes)
        }

        assertEquals(1, decoded.entries.size)
        assertEquals(1, collector.diagnostics.size)
        assertEquals("INVALID_PRICE_ENTRY", collector.diagnostics[0].code)
        assertTrue(collector.diagnostics[0].message.contains("amount"))
    }
}
