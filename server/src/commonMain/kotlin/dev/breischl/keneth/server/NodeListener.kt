package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.SoftDisconnect

/**
 * Callback interface for observing [EpNode] events.
 *
 * Provides both session-level events (for all connections) and peer-level events (for configured
 * peers that have completed the EP handshake). All methods have default no-op implementations —
 * override only the events you care about. Exceptions thrown by listener methods are silently
 * swallowed to avoid disrupting node operation.
 *
 * **Callbacks are synchronous.** Each method is called on the coroutine that drives
 * the session, so it must return promptly. Implementors that need to perform I/O
 * or other slow work must dispatch it to their own coroutine scope.
 *
 * State is passed as immutable [SessionSnapshot]s so that the state captured in a
 * callback cannot become stale after the method returns.
 */
interface NodeListener {

    // -- Session-level events (fire for all connections, peer-linked or not) --

    /** A new connection was accepted and a session created (state is [SessionState.AWAITING_SESSION]). */
    fun onSessionCreated(session: SessionSnapshot) {}

    /** Session handshake completed successfully (state is now [SessionState.ACTIVE]). */
    fun onSessionActive(session: SessionSnapshot) {}

    /** Session handshake failed (wrong first message, parse error, etc.). */
    fun onSessionHandshakeFailed(session: SessionSnapshot, reason: String) {}

    /** Session is disconnecting ([SoftDisconnect] received or sent). */
    fun onSessionDisconnecting(session: SessionSnapshot, softDisconnect: SoftDisconnect?) {}

    /** Session is fully closed and removed from the node. */
    fun onSessionClosed(session: SessionSnapshot) {}

    /** An error occurred processing a session. */
    fun onSessionError(session: SessionSnapshot, error: Throwable) {}

    /** A message was received from a device on an active session. */
    fun onMessageReceived(session: SessionSnapshot, message: Message) {}

    /** A message was sent to a device on a session. */
    fun onMessageSent(session: SessionSnapshot, message: Message) {}

    // -- Peer-level events (fire only for sessions linked to a configured peer) --

    /** A configured peer has completed the EP handshake and is now connected. */
    fun onPeerConnected(session: SessionSnapshot) {}

    /** A configured peer's session was closed. The peer remains configured but disconnected. */
    fun onPeerDisconnected(session: SessionSnapshot) {}

}
