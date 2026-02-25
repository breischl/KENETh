package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.SoftDisconnect

/**
 * Callback interface for observing [EpServer] session lifecycle events.
 *
 * All methods have default no-op implementations — override only the events
 * you care about. Exceptions thrown by listener methods are silently swallowed
 * to avoid disrupting server operation.
 *
 * **Callbacks are synchronous.** Each method is called on the coroutine that drives
 * the session, so it must return promptly. Implementors that need to perform I/O
 * or other slow work must dispatch it to their own coroutine scope.
 *
 * Session and peer state is passed as immutable snapshots ([DeviceSessionSnapshot],
 * [PeerSnapshot]) so that the state captured in a callback cannot become stale
 * after the method returns.
 */
interface ServerListener {
    /** A new connection was accepted and a session created (state is [SessionState.AWAITING_SESSION]). */
    fun onSessionCreated(session: DeviceSessionSnapshot) {}

    /** Session handshake completed successfully (state is now [SessionState.ACTIVE]). */
    fun onSessionActive(session: DeviceSessionSnapshot) {}

    /** A message was received from a device on an active session. */
    fun onMessageReceived(session: DeviceSessionSnapshot, message: Message) {}

    /** Session handshake failed (wrong first message, parse error, etc.). */
    fun onSessionHandshakeFailed(session: DeviceSessionSnapshot, reason: String) {}

    /** Session is disconnecting ([SoftDisconnect] received or sent). */
    fun onSessionDisconnecting(session: DeviceSessionSnapshot, softDisconnect: SoftDisconnect?) {}

    /** Session is fully closed and removed from the server. */
    fun onSessionClosed(session: DeviceSessionSnapshot) {}

    /** An error occurred processing a session. */
    fun onSessionError(session: DeviceSessionSnapshot, error: Throwable) {}

    /** A configured peer's session became [SessionState.ACTIVE]. */
    fun onPeerConnected(peer: PeerSnapshot) {}

    /** A configured peer's session was closed. The peer remains configured but disconnected. */
    fun onPeerDisconnected(peer: PeerSnapshot) {}

    /** A message was sent to a device on an active session. */
    fun onMessageSent(session: DeviceSessionSnapshot, message: Message) {}
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
