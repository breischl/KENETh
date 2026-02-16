package dev.breischl.keneth.transport

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.parsing.LenientMessageParser
import dev.breischl.keneth.core.parsing.MessageParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import java.io.Closeable

/**
 * A high-level transport for sending and receiving EnergyNet [Message] objects
 * over a [FrameTransport].
 *
 * Wraps a [FrameTransport] and handles encoding/decoding between messages and frames,
 * so callers don't need to deal with frame construction, CBOR serialization,
 * or message parsing directly.
 *
 * Example usage:
 * ```kotlin
 * val frameTransport = RawTcpClientTransport("charger.local", 56540)
 * val transport = MessageTransport(frameTransport)
 * try {
 *     // Send a message
 *     transport.send(SessionParameters(identity = "vehicle-1"))
 *
 *     // Receive messages
 *     transport.receive().collect { received ->
 *         if (received.succeeded) {
 *             when (val msg = received.message!!) {
 *                 is Ping -> println("Got ping")
 *                 is SessionParameters -> println("Session: ${msg.identity}")
 *                 else -> {}
 *             }
 *         }
 *     }
 * } finally {
 *     transport.close()
 * }
 * ```
 *
 * By default, uses [LenientMessageParser] which wraps unknown message types in
 * [dev.breischl.keneth.core.messages.UnknownMessage] with a warning diagnostic.
 * Pass a [dev.breischl.keneth.core.parsing.StrictMessageParser] for strict validation.
 *
 * @param frameTransport The underlying frame transport.
 * @param messageParser The parser to use for decoding received messages.
 */
class MessageTransport(
    private val frameTransport: FrameTransport,
    private val messageParser: MessageParser = LenientMessageParser()
) : Closeable {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    /**
     * Sends a message over the transport with optional headers.
     *
     * @param message The message to send.
     * @param headers Optional frame headers.
     */
    suspend fun send(message: Message, headers: Map<UInt, Any> = emptyMap()) {
        @Suppress("UNCHECKED_CAST")
        val payload = cbor.encodeToByteArray(
            message.payloadSerializer as KSerializer<Message>,
            message
        )
        frameTransport.send(Frame(headers, message.typeId, payload))
    }

    /**
     * Returns a flow of received messages.
     *
     * Each [ReceivedMessage] carries the parsed message (or null on failure),
     * frame headers, and any diagnostics from both frame decoding and message parsing.
     */
    fun receive(): Flow<ReceivedMessage> {
        return frameTransport.receive().map { frameResult ->
            if (!frameResult.succeeded) {
                ReceivedMessage(null, emptyMap(), frameResult.diagnostics)
            } else {
                val frame = frameResult.value!!
                val messageResult = messageParser.parseMessage(frame)
                val diagnostics = frameResult.diagnostics + messageResult.diagnostics
                ReceivedMessage(messageResult.value, frame.headers, diagnostics)
            }
        }
    }

    override fun close() {
        frameTransport.close()
    }
}
