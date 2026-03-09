package dev.breischl.keneth.server

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.transport.FrameTransport
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor

/** Shared CBOR codec for tests. */
internal val testCbor = Cbor { ingnoreUnknownKeys = true }

/**
 * Encodes [message] to a CBOR byte array using [testCbor].
 *
 * The cast to [KSerializer]`<Message>` is safe because the serializer and the value come from the
 * same concrete object — the serializer only receives values of exactly that type, so the invariant
 * requirement is never violated at runtime.
 */
internal fun encodeMessage(message: Message): ByteArray {
    @Suppress("UNCHECKED_CAST")
    return testCbor.encodeToByteArray(message.payloadSerializer as KSerializer<Message>, message)
}

/** Wraps [message] in a [ParseResult.success] [Frame]. */
internal fun frameResultFor(message: Message): ParseResult<Frame> {
    val payload = encodeMessage(message)
    return ParseResult.success(Frame(emptyMap(), message.typeId, payload), emptyList())
}

/**
 * Creates a [ChannelFakeFrameTransport] pre-populated with [messages] and returns it paired
 * with a [MessageTransport] wrapping it.
 */
internal suspend fun channelTransportWithMessages(vararg messages: Message): Pair<ChannelFakeFrameTransport, MessageTransport> {
    val fake = ChannelFakeFrameTransport()
    for (msg in messages) {
        fake.enqueue(frameResultFor(msg))
    }
    return fake to MessageTransport(fake)
}

/** Encodes [message] and enqueues it into this transport. */
internal suspend fun ChannelFakeFrameTransport.enqueueMessage(message: Message) {
    enqueue(frameResultFor(message))
}

/**
 * A fake [FrameTransport] backed by a [Channel], so the receive flow stays open
 * until the channel is explicitly closed. Useful for tests that need the
 * session to remain ACTIVE while they call server APIs.
 */
internal class ChannelFakeFrameTransport(
    private val channel: Channel<ParseResult<Frame>> = Channel(Channel.UNLIMITED)
) : FrameTransport {
    val sentFrames = mutableListOf<Frame>()
    var closed = false

    override suspend fun send(frame: Frame) {
        sentFrames.add(frame)
    }

    override fun receive(): Flow<ParseResult<Frame>> = channel.consumeAsFlow()

    override fun close() {
        closed = true
        channel.close()
    }

    suspend fun enqueue(frame: ParseResult<Frame>) {
        channel.send(frame)
    }
}
