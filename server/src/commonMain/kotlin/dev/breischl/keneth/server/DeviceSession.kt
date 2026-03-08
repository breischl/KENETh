package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.channels.Channel

/**
 * Represents a connected device and its current state.
 *
 * Each session tracks the device's identity (from the handshake).
 *
 * The server manages session state internally. External code can read
 * properties and call [send] to send messages to the device.
 *
 * @property id Server-assigned unique session ID.
 */
class DeviceSession internal constructor(
    val id: String,
    internal val transport: MessageTransport,
) {
    /** The device's identity, populated after a successful handshake. */
    var sessionParameters: SessionParameters? = null
        internal set

    /** Current lifecycle state of this session. */
    var state: SessionState = SessionState.AWAITING_SESSION
        internal set

    /**
     * Signalled on every received message (and on transfer-start) so the receive watchdog
     * can reset its deadline without relying on wall-clock time.
     */
    internal val receiveHeartbeat = Channel<Unit>(Channel.CONFLATED)

    /** Called after each successful [send]. Set by [EpNode] to fire listener notifications. */
    internal var afterSend: ((Message) -> Unit)? = null

    /** Send a message to this device. */
    suspend fun send(message: Message) {
        transport.send(message)
        afterSend?.invoke(message)
    }

    /**
     * Close the underlying transport and mark this session as [SessionState.CLOSED].
     *
     * Internal — external callers should use [EpNode.disconnect] to ensure
     * proper cleanup (peer unlinking, listener callbacks).
     */
    internal fun close() {
        state = SessionState.CLOSED
        transport.close()
    }
}
