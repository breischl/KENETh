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
 * Represents an electrical current measurement in amperes.
 *
 * This is an inline value class for type safety, ensuring that current values
 * cannot be accidentally confused with other numeric types like voltage or power.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property amperes The current value in amperes (A).
 */
@JvmInline
@Serializable(with = CurrentSerializer::class)
value class Current(val amperes: Double) {
    companion object {
        /** CBOR type identifier for Current values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x11
    }
}

/**
 * CBOR serializer for [Current] values.
 *
 * Serializes current as a CBOR map with the type ID as key: `{ 0x11: <amperes> }`.
 */
object CurrentSerializer : KSerializer<Current> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Current")

    override fun serialize(encoder: Encoder, value: Current) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildDoubleMap(Current.TYPE_ID, value.amperes)
        )
    }

    override fun deserialize(decoder: Decoder): Current {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Current.TYPE_ID) ?: error("Missing current value")
        return Current(value.toDoubleValue())
    }
}
