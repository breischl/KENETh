package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Message

/**
 * High-level callback interface for observing [EpNode] events.
 *
 * Hides low-level session details and exposes peer-level lifecycle events.
 * All methods have default no-op implementations — override only the events you
 * care about. Exceptions thrown by listener methods are silently swallowed
 * to avoid disrupting node operation.
 *
 * **Callbacks are synchronous.** Each method is called on the coroutine that drives
 * the session, so it must return promptly. Implementors that need to perform I/O
 * or other slow work must dispatch it to their own coroutine scope.
 *
 * Parameters are passed as immutable snapshots ([PeerSnapshot], [EnergyTransferSnapshot])
 * so that the state captured in a callback cannot become stale after the method returns.
 */
interface NodeListener {
    /** A configured peer has completed the EP handshake and is now connected. */
    fun onPeerConnected(peer: PeerSnapshot) {}

    /** A configured peer's session was closed. The peer remains configured but disconnected. */
    fun onPeerDisconnected(peer: PeerSnapshot) {}

    /**
     * A peer sent updated Supply, Demand, or Storage parameters.
     *
     * This fires after the peer's session has been updated with the new values,
     * captured in [peer].
     */
    fun onPeerParametersUpdated(peer: PeerSnapshot, message: Message) {}

    /** An energy transfer has been started for a peer. */
    fun onTransferStarted(transfer: EnergyTransferSnapshot) {}

    /** An energy transfer has been stopped (manually, or due to peer disconnect/error). */
    fun onTransferStopped(transfer: EnergyTransferSnapshot) {}

    /** A message was sent to a peer. */
    fun onMessageSent(peer: PeerSnapshot, message: Message) {}

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
