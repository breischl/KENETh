package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.transport.TransportListener

/**
 * Configuration for an [EpNode].
 *
 * @property identity The server's identity, sent to each peer during the EP handshake.
 * @property acceptor Strategy for accepting inbound connections, or null to disable listening.
 * @property transportListener Optional listener for transport-level events (frame/message I/O).
 */
data class NodeConfig(
    val identity: SessionParameters,
    val acceptor: InboundAcceptor? = null,
    val transportListener: TransportListener? = null,
)
