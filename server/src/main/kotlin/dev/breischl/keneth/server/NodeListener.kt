package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Message

/**
 * High-level callback interface for observing [EpNode] events.
 *
 * Hides low-level session details and exposes peer-level lifecycle events.
 * All methods have default no-op implementations — override only the events you
 * care about. Exceptions thrown by listener methods are silently swallowed
 * to avoid disrupting node operation.
 */
interface NodeListener {
    /** A configured peer has completed the EP handshake and is now connected. */
    fun onPeerConnected(peer: Peer) {}

    /** A configured peer's session was closed. The peer remains configured but disconnected. */
    fun onPeerDisconnected(peer: Peer) {}

    /**
     * A peer sent updated Supply, Demand, or Storage parameters.
     *
     * This fires after the peer's session has been updated with the new values,
     * accessible via [Peer.latestSupply], [Peer.latestDemand], and [Peer.latestStorage].
     */
    fun onPeerParametersUpdated(peer: Peer, message: Message) {}

    /** An energy transfer has been started for a peer. */
    fun onTransferStarted(transfer: EnergyTransfer) {}

    /** An energy transfer has been stopped (manually, or due to peer disconnect/error). */
    fun onTransferStopped(transfer: EnergyTransfer) {}

    /** An unrecoverable error occurred (e.g., session processing failure). */
    fun onError(error: Throwable) {}
}

/**
 * Safely invokes a [NodeListener] callback, swallowing any exception.
 * When the receiver is null, this compiles to a no-op.
 */
internal inline fun NodeListener?.safeNotify(block: NodeListener.() -> Unit) {
    if (this != null) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }
}
