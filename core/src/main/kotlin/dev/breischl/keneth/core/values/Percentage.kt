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
 * Represents a percentage value.
 *
 * This is an inline value class for type safety. The value is stored as a
 * percentage (0-100 range typically, though values outside this range are allowed).
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property percent The percentage value (e.g., 85.5 for 85.5%).
 */
@JvmInline
@Serializable(with = PercentageSerializer::class)
value class Percentage(val percent: Double) {
    companion object {
        /** CBOR type identifier for Percentage values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x14
    }
}

/**
 * CBOR serializer for [Percentage] values.
 *
 * Serializes percentage as a CBOR map with the type ID as key: `{ 0x14: <percent> }`.
 */
object PercentageSerializer : KSerializer<Percentage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Percentage")

    override fun serialize(encoder: Encoder, value: Percentage) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildDoubleMap(Percentage.TYPE_ID, value.percent)
        )
    }

    override fun deserialize(decoder: Decoder): Percentage {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Percentage.TYPE_ID) ?: error("Missing percentage value")
        return Percentage(value.toDoubleValue())
    }
}
