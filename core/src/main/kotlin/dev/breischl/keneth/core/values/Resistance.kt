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
 * Represents an electrical resistance measurement in ohms.
 *
 * This is an inline value class for type safety, commonly used for
 * isolation resistance measurements in EV charging contexts.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property ohms The resistance value in ohms (Ω).
 */
@JvmInline
@Serializable(with = ResistanceSerializer::class)
value class Resistance(val ohms: Double) {
    companion object {
        /** CBOR type identifier for Resistance values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x15
    }
}

/**
 * CBOR serializer for [Resistance] values.
 *
 * Serializes resistance as a CBOR map with the type ID as key: `{ 0x15: <ohms> }`.
 */
object ResistanceSerializer : KSerializer<Resistance> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Resistance")

    override fun serialize(encoder: Encoder, value: Resistance) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildDoubleMap(Resistance.TYPE_ID, value.ohms)
        )
    }

    override fun deserialize(decoder: Decoder): Resistance {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Resistance.TYPE_ID) ?: error("Missing resistance value")
        return Resistance(value.toDoubleValue())
    }
}
