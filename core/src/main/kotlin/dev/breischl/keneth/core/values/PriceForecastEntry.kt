package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.values.SerializerUtils.asCborMap
import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toDoubleValue
import dev.breischl.keneth.core.values.SerializerUtils.toStringValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborArray
import net.orandja.obor.data.CborObject
import kotlin.time.Instant

/**
 * Represents a single price entry in a price forecast.
 *
 * Each entry contains a timestamp indicating when the price becomes effective,
 * the price amount, and the currency code. Defined in EnergyNet Protocol section 3.1.2.
 *
 * @property timestamp The time when this price becomes effective.
 * @property amount The price amount.
 * @property currency The ISO 4217 currency code (e.g., "USD", "EUR").
 */
@Serializable(with = PriceForecastEntrySerializer::class)
data class PriceForecastEntry(
    val timestamp: Instant,
    val amount: Amount,
    val currency: String
)

/**
 * CBOR serializer for [PriceForecastEntry] values.
 *
 * Serializes price entry as a CBOR array with value-type wrapped fields:
 * `[{0x03: "timestamp"}, {0x02: amount}, {0x05: "currency"}]`.
 */
object PriceForecastEntrySerializer : KSerializer<PriceForecastEntry> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PriceEntry")

    override fun serialize(encoder: Encoder, value: PriceForecastEntry) {
        val array = buildPriceEntryArray(value)
        encoder.encodeSerializableValue(CborArray.serializer(), array)
    }

    override fun deserialize(decoder: Decoder): PriceForecastEntry {
        val array = decoder.decodeSerializableValue(CborArray.serializer())
        return parsePriceEntry(array)
    }

    /**
     * Build the CBOR array that represents a PriceEntry
     */
    internal fun buildPriceEntryArray(entry: PriceForecastEntry): CborArray {
        return CborObject.buildArray {
            add(SerializerUtils.buildStringMap(Timestamp.TYPE_ID, entry.timestamp.toString()))
            add(SerializerUtils.buildDoubleMap(Amount.TYPE_ID, entry.amount.value))
            add(SerializerUtils.buildStringMap(Currency.TYPE_ID, entry.currency))
        }
    }

    internal fun parsePriceEntry(array: CborArray): PriceForecastEntry {
        require(array.size == 3) { "PriceEntry array must have exactly 3 elements" }
        val timestampStr = array[0].asCborMap().getByIntKey(Timestamp.TYPE_ID)?.toStringValue()
            ?: error("Missing timestamp in PriceEntry")
        val amount = array[1].asCborMap().getByIntKey(Amount.TYPE_ID)?.toDoubleValue()
            ?: error("Missing amount in PriceEntry")
        val currency = array[2].asCborMap().getByIntKey(Currency.TYPE_ID)?.toStringValue()
            ?: error("Missing currency in PriceEntry")
        return PriceForecastEntry(
            timestamp = Instant.parse(timestampStr),
            amount = Amount(amount),
            currency = currency
        )
    }
}