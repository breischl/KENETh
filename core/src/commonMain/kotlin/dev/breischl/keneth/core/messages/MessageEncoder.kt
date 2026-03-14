package dev.breischl.keneth.core.messages

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.frames.FrameCodec
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor

/**
 * Encodes EP [Message] instances into frame byte arrays.
 *
 * This is the encoding counterpart to [dev.breischl.keneth.core.parsing.LenientMessageParser],
 * which decodes frames into messages.
 */
object MessageEncoder {

    // "ingnoreUnknownKeys" is a typo in the OBOR library API (should be "ignore")
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    /**
     * Encodes a [Message] into a complete EP frame byte array (magic bytes + headers + typeId + CBOR payload).
     *
     * @param message The message to encode.
     * @return The encoded frame bytes.
     */
    fun encode(message: Message): ByteArray {
        @Suppress("UNCHECKED_CAST")
        val payload = cbor.encodeToByteArray(
            message.payloadSerializer as KSerializer<Message>,
            message
        )
        val frame = Frame(emptyMap(), message.typeId, payload)
        return FrameCodec.encode(frame)
    }
}
