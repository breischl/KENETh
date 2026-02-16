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
 * Represents an electrical power measurement in watts.
 *
 * This is an inline value class for type safety, ensuring that power values
 * cannot be accidentally confused with other numeric types like voltage or current.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property watts The power value in watts (W).
 */
@JvmInline
@Serializable(with = PowerSerializer::class)
value class Power(val watts: Double) {
    companion object {
        /** CBOR type identifier for Power values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x12
    }
}

/**
 * CBOR serializer for [Power] values.
 *
 * Serializes power as a CBOR map with the type ID as key: `{ 0x12: <watts> }`.
 */
object PowerSerializer : KSerializer<Power> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Power")

    override fun serialize(encoder: Encoder, value: Power) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildDoubleMap(Power.TYPE_ID, value.watts)
        )
    }

    override fun deserialize(decoder: Decoder): Power {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Power.TYPE_ID) ?: error("Missing power value")
        return Power(value.toDoubleValue())
    }
}
