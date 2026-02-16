package dev.breischl.keneth.core.messages

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A message with an unrecognized type ID.
 *
 * This is used by lenient parsers to preserve messages that have
 * unknown type IDs, allowing them to be forwarded or logged.
 *
 * @property typeId The unrecognized message type ID.
 * @property rawPayload The unparsed CBOR payload.
 */
data class UnknownMessage(
    override val typeId: UInt,
    val rawPayload: ByteArray
) : Message() {
    override val payloadSerializer get() = UnknownMessageSerializer

    companion object {
        fun serializer() = UnknownMessageSerializer
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnknownMessage) return false
        if (typeId != other.typeId) return false
        if (!rawPayload.contentEquals(other.rawPayload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = typeId.hashCode()
        result = 31 * result + rawPayload.contentHashCode()
        return result
    }

    object UnknownMessageSerializer : KSerializer<UnknownMessage> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UnknownMessage")

        override fun serialize(encoder: Encoder, value: UnknownMessage) {
            encoder.encodeSerializableValue(ByteArraySerializer(), value.rawPayload)
        }

        override fun deserialize(decoder: Decoder): UnknownMessage {
            error("UnknownMessage cannot be deserialized without a type ID")
        }
    }
}