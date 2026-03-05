package dev.breischl.keneth.core.messages

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A keep-alive message with no payload.
 *
 * Ping messages are used to verify that the connection is still active
 * and to prevent connection timeouts.
 * Defined in EnergyNet Protocol section 4.1.
 */
data object Ping : Message() {
    override val typeId: UInt = 0xFFFF_FFFFu
    override val payloadSerializer get() = PingSerializer

    object PingSerializer : KSerializer<Ping> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Ping")

        @OptIn(ExperimentalSerializationApi::class)
        override fun serialize(encoder: Encoder, value: Ping) {
            encoder.encodeNull()
        }

        override fun deserialize(decoder: Decoder): Ping {
            // Spec says Ping payload is null (F6), but be lenient on receive
            return Ping
        }
    }
}