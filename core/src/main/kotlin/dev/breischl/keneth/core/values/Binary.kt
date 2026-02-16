package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toByteArrayValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap

/**
 * Represents binary data as a byte array.
 *
 * This is an inline value class for type safety when transmitting
 * raw binary data in the EnergyNet Protocol.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property bytes The binary data as a byte array.
 */
@JvmInline
@Serializable(with = BinarySerializer::class)
value class Binary(val bytes: ByteArray) {
    companion object {
        /** CBOR type identifier for Binary values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x04
    }
}

/**
 * CBOR serializer for [Binary] values.
 *
 * Serializes binary data as a CBOR map with the type ID as key: `{ 0x04: <bytes> }`.
 */
object BinarySerializer : KSerializer<Binary> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Binary")

    override fun serialize(encoder: Encoder, value: Binary) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildByteArrayMap(Binary.TYPE_ID, value.bytes)
        )
    }

    override fun deserialize(decoder: Decoder): Binary {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Binary.TYPE_ID) ?: error("Missing binary value")
        return Binary(value.toByteArrayValue())
    }
}
