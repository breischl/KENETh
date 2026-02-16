package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toBooleanValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap

/**
 * Represents a boolean flag value in the EnergyNet Protocol.
 *
 * This is an inline value class that wraps a boolean for type safety
 * when a typed flag value is needed in protocol messages.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property value The boolean flag value.
 */
@JvmInline
@Serializable(with = FlagSerializer::class)
value class Flag(val value: Boolean) {
    companion object {
        /** CBOR type identifier for Flag values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x01
    }
}

/**
 * CBOR serializer for [Flag] values.
 *
 * Serializes flag as a CBOR map with the type ID as key: `{ 0x01: <value> }`.
 */
object FlagSerializer : KSerializer<Flag> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Flag")

    override fun serialize(encoder: Encoder, value: Flag) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildBooleanMap(Flag.TYPE_ID, value.value)
        )
    }

    override fun deserialize(decoder: Decoder): Flag {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Flag.TYPE_ID) ?: error("Missing flag value")
        return Flag(value.toBooleanValue())
    }
}
