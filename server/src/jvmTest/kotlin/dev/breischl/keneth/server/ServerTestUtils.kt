package dev.breischl.keneth.server

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.transport.FrameTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

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
