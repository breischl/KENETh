package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toStringValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap

/**
 * Represents a currency code (e.g., "USD", "EUR", "GBP").
 *
 * This is an inline value class for type safety, typically used in
 * conjunction with [Amount] for pricing information.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property code The ISO 4217 currency code (e.g., "USD", "EUR").
 */
@JvmInline
@Serializable(with = CurrencySerializer::class)
value class Currency(val code: String) {
    companion object {
        /** CBOR type identifier for Currency values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x05
    }
}

/**
 * CBOR serializer for [Currency] values.
 *
 * Serializes currency as a CBOR map with the type ID as key: `{ 0x05: <code> }`.
 */
object CurrencySerializer : KSerializer<Currency> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Currency")

    override fun serialize(encoder: Encoder, value: Currency) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildStringMap(Currency.TYPE_ID, value.code)
        )
    }

    override fun deserialize(decoder: Decoder): Currency {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Currency.TYPE_ID) ?: error("Missing currency value")
        return Currency(value.toStringValue())
    }
}
