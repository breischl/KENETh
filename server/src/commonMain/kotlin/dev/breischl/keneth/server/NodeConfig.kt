package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.transport.TransportListener

/**
 * Configuration for an [EpNode].
 *
 * @property identity The server's identity, sent to each peer during the EP handshake.
 * @property listenPort TCP port to listen on for inbound connections, or null to disable listening.
 *   Use 0 for an ephemeral port.
 * @property transportListener Optional listener for transport-level events (frame/message I/O).
 */
data class NodeConfig(
    val identity: SessionParameters,
    val listenPort: Int? = null,
    val transportListener: TransportListener? = null,
)
