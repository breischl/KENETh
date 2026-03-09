package dev.breischl.keneth.server

import dev.breischl.keneth.transport.InMemoryFrameTransport
import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.PeerConnector
import dev.breischl.keneth.transport.TransportListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An in-memory [InboundAcceptor] and [PeerConnector] combined into a single rendezvous object,
 * for connecting two in-process [EpNode]s without network I/O.
 *
 * Pass the same instance as the acceptor for one node and the connector for another:
 *
 * ```kotlin
 * val acceptor = InMemoryInboundAcceptor()
 *
 * val nodeA = EpNode(identity = identityA, acceptor = acceptor)
 * val nodeB = EpNode(identity = identityB)
 *
 * nodeA.addPeer(PeerConfig.Inbound("node-b"))
 * nodeB.addPeer(PeerConfig.Outbound("node-a", connector = acceptor))
 *
 * nodeA.start()
 * nodeB.start()
 * ```
 *
 * When the outbound node calls [connect] (via [EpNode.addPeer]), an [InMemoryFrameTransport]
 * pair is created and the inbound side is queued internally. When the accepting node calls
 * [start], a coroutine drains the queue and passes each transport to [EpNode.accept].
 *
 * @param coroutineContext Additional coroutine context (e.g., a test dispatcher).
 */
class InMemoryInboundAcceptor(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : InboundAcceptor, PeerConnector {

    private val pendingConnections = Channel<MessageTransport>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    /**
     * Called by the outbound node when it establishes a connection.
     *
     * Creates an [InMemoryFrameTransport] pair, queues the remote side for the accepting node's
     * [EpNode.accept], and returns the local side for the outbound node's session.
     */
    override suspend fun connect(listener: TransportListener?): MessageTransport {
        val (local, remote) = InMemoryFrameTransport.createPair()
        local.listener = listener
        pendingConnections.send(MessageTransport(remote))
        return MessageTransport(local, listener = listener)
    }

    /**
     * Starts the accept loop. Drains queued connections and passes each to [EpNode.accept].
     */
    override fun start(node: EpNode) {
        scope.launch {
            for (transport in pendingConnections) {
                node.accept(transport)
            }
        }
    }

    /** Closes the pending connection channel and cancels the accept loop. */
    override fun close() {
        pendingConnections.close()
        scope.cancel()
    }
}
