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
 * Represents a text string value in the EnergyNet Protocol.
 *
 * This is an inline value class that wraps a string for type safety
 * when a typed text value is needed in protocol messages.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property value The text string value.
 */
@JvmInline
@Serializable(with = TextSerializer::class)
value class Text(val value: String) {
    companion object {
        /** CBOR type identifier for Text values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x00
    }
}

/**
 * CBOR serializer for [Text] values.
 *
 * Serializes text as a CBOR map with the type ID as key: `{ 0x00: <value> }`.
 */
object TextSerializer : KSerializer<Text> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Text")

    override fun serialize(encoder: Encoder, value: Text) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildStringMap(Text.TYPE_ID, value.value)
        )
    }

    override fun deserialize(decoder: Decoder): Text {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Text.TYPE_ID) ?: error("Missing text value")
        return Text(value.toStringValue())
    }
}
