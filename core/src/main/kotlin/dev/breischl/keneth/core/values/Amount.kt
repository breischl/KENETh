package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toDoubleValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap

/**
 * Represents a monetary or numeric amount value.
 *
 * This is an inline value class for type safety, typically used for
 * pricing information in conjunction with a [Currency] value.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property value The amount value.
 */
@JvmInline
@Serializable(with = AmountSerializer::class)
value class Amount(val value: Double) {
    companion object {
        /** CBOR type identifier for Amount values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x02
    }
}

/**
 * CBOR serializer for [Amount] values.
 *
 * Serializes amount as a CBOR map with the type ID as key: `{ 0x02: <value> }`.
 */
object AmountSerializer : KSerializer<Amount> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Amount")

    override fun serialize(encoder: Encoder, value: Amount) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildDoubleMap(Amount.TYPE_ID, value.value)
        )
    }

    override fun deserialize(decoder: Decoder): Amount {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val v = map.getByIntKey(Amount.TYPE_ID) ?: error("Missing amount value")
        return Amount(v.toDoubleValue())
    }
}
