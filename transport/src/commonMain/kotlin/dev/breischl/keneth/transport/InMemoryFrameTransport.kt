package dev.breischl.keneth.transport

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.parsing.ParseResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach

/**
 * An in-memory [FrameTransport] backed by [Channel]s, suitable for testing and in-process simulation.
 *
 * Two transports are wired together via [createPair]: frames sent on one are received by the other.
 * For most use cases, prefer [InMemoryPeerConnector] which handles pairing automatically.
 *
 * Set [listener] to observe frame-level events. [InMemoryPeerConnector] sets this automatically
 * when its [PeerConnector.connect] method is called.
 */
class InMemoryFrameTransport internal constructor(
    private val inbound: Channel<ParseResult<Frame>>,
    private val outbound: Channel<ParseResult<Frame>>,
) : FrameTransport {

    /** Optional listener for frame-level transport events. */
    var listener: TransportListener? = null

    override suspend fun send(frame: Frame) {
        // No wire encoding for in-memory transport; pass empty bytes to listener
        listener.safeNotify { onFrameSending(frame, byteArrayOf()) }
        outbound.send(ParseResult.success(frame, emptyList()))
        listener.safeNotify { onFrameSent(frame, byteArrayOf()) }
    }

    override fun receive(): Flow<ParseResult<Frame>> = inbound.consumeAsFlow().onEach { result ->
        listener.safeNotify { onFrameReceived(result) }
    }

    /**
     * Closes only the outbound channel, signalling EOF to the paired transport's receive flow
     * without preventing the paired transport from sending back.
     *
     * Analogous to TCP `shutdownOutput()`: useful for tests that pre-populate incoming frames
     * and want the receive flow to complete naturally after draining them.
     */
    fun shutdownOutput() {
        outbound.close()
    }

    override fun close() {
        inbound.close()
        outbound.close()
        listener.safeNotify { onDisconnected() }
    }

    companion object {
        /**
         * Creates a pair of [InMemoryFrameTransport]s wired together.
         *
         * Frames sent on the first transport are received by the second, and vice versa.
         * For most use cases, prefer [InMemoryPeerConnector] which handles pairing automatically.
         */
        fun createPair(): Pair<InMemoryFrameTransport, InMemoryFrameTransport> {
            val channelA = Channel<ParseResult<Frame>>(Channel.UNLIMITED)
            val channelB = Channel<ParseResult<Frame>>(Channel.UNLIMITED)
            return InMemoryFrameTransport(channelA, channelB) to InMemoryFrameTransport(channelB, channelA)
        }
    }
}

/**
 * A [PeerConnector] for in-process simulation without network I/O.
 *
 * Creates a pair of [InMemoryFrameTransport]s internally. One side is used when
 * [connect] is called (the outbound/local side); the other is exposed as [remoteTransport]
 * and should be passed to the peer's server via `server.accept(MessageTransport(connector.remoteTransport))`.
 *
 * Example:
 * ```kotlin
 * val connector = InMemoryPeerConnector()
 * nodeA.addPeer(PeerConfig.Outbound("node-b", connector))
 * nodeB.server.accept(MessageTransport(connector.remoteTransport))
 * ```
 */
class InMemoryPeerConnector : PeerConnector {
    private val channelA = Channel<ParseResult<Frame>>(Channel.UNLIMITED)
    private val channelB = Channel<ParseResult<Frame>>(Channel.UNLIMITED)

    private val localTransport = InMemoryFrameTransport(channelA, channelB)

    /** The remote end of the connection — accept this into the peer's server. */
    val remoteTransport: InMemoryFrameTransport = InMemoryFrameTransport(channelB, channelA)

    override suspend fun connect(listener: TransportListener?): MessageTransport {
        localTransport.listener = listener
        return MessageTransport(localTransport, listener = listener)
    }
}
