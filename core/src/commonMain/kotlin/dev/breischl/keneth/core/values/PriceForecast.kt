package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.diagnostics.DiagnosticContext
import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborArray
import net.orandja.obor.data.CborMap
import net.orandja.obor.data.CborObject

/**
 * Represents a forecast of energy prices over time.
 *
 * Contains a list of [PriceForecastEntry] values, each representing the price
 * at a specific point in time. Used to communicate time-of-use pricing
 * or dynamic pricing information.
 *
 * @property entries The list of price entries, ordered by timestamp.
 */
@Serializable(with = PriceForecastSerializer::class)
data class PriceForecast(
    val entries: List<PriceForecastEntry>
) {
    companion object {
        /** CBOR type identifier for PriceForecast values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x30
    }
}

/**
 * CBOR serializer for [PriceForecast] values.
 *
 * Serializes price forecast as a CBOR map:
 * `{ 0x30: [[{0x03: "ts"}, {0x02: amt}, {0x05: "cur"}], ...] }`.
 */
object PriceForecastSerializer : KSerializer<PriceForecast> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PriceForecast")

    override fun serialize(encoder: Encoder, value: PriceForecast) {
        val entriesArray = CborObject.buildArray {
            for (entry in value.entries) {
                add(PriceForecastEntrySerializer.buildPriceEntryArray(entry))
            }
        }
        val map = CborObject.buildMap {
            put(CborObject.positive(PriceForecast.TYPE_ID), entriesArray)
        }
        encoder.encodeSerializableValue(CborMap.serializer(), map)
    }

    override fun deserialize(decoder: Decoder): PriceForecast {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val entriesObj = map.getByIntKey(PriceForecast.TYPE_ID)
            ?: error("Missing price forecast value")
        val entriesArray = entriesObj as? CborArray
            ?: error("Expected CborArray for PriceForecast entries")

        val collector = DiagnosticContext.get()
        val entries = mutableListOf<PriceForecastEntry>()
        for (entryObj in entriesArray) {
            val entryArray = entryObj as? CborArray
            if (entryArray == null) {
                collector?.warning("INVALID_PRICE_ENTRY", "Expected CborArray for PriceEntry, skipping")
                continue
            }
            val entry = PriceForecastEntrySerializer.parsePriceEntry(entryArray, collector)
            if (entry != null) entries.add(entry)
        }
        return PriceForecast(entries)
    }
}
