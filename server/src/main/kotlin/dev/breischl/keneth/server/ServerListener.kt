package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.SoftDisconnect

/**
 * Callback interface for observing [EpServer] session lifecycle events.
 *
 * All methods have default no-op implementations — override only the events
 * you care about. Exceptions thrown by listener methods are silently swallowed
 * to avoid disrupting server operation.
 */
interface ServerListener {
    /** A new connection was accepted and a session created (state is [SessionState.AWAITING_SESSION]). */
    fun onSessionCreated(session: DeviceSession) {}

    /** Session handshake completed successfully (state is now [SessionState.ACTIVE]). */
    fun onSessionActive(session: DeviceSession) {}

    /** A message was received from a device on an active session. */
    fun onMessageReceived(session: DeviceSession, message: Message) {}

    /** Session handshake failed (wrong first message, parse error, etc.). */
    fun onSessionHandshakeFailed(session: DeviceSession, reason: String) {}

    /** Session is disconnecting ([SoftDisconnect] received or sent). */
    fun onSessionDisconnecting(session: DeviceSession, softDisconnect: SoftDisconnect?) {}

    /** Session is fully closed and removed from the server. */
    fun onSessionClosed(session: DeviceSession) {}

    /** An error occurred processing a session. */
    fun onSessionError(session: DeviceSession, error: Throwable) {}

    /** A configured peer's session became [SessionState.ACTIVE]. */
    fun onPeerConnected(peer: Peer) {}

    /** A configured peer's session was closed. The peer remains configured but disconnected. */
    fun onPeerDisconnected(peer: Peer) {}
}

/**
 * Safely invokes a [ServerListener] callback, swallowing any exception.
 * When the receiver is null, this compiles to a no-op.
 */
internal inline fun ServerListener?.safeNotify(block: ServerListener.() -> Unit) {
    if (this != null) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }
}
