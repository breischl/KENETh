package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.transport.MessageTransport

/**
 * Represents a connected device and its current state.
 *
 * Each session tracks the device's identity (from the handshake) and the
 * latest supply, demand, and storage parameters the device has advertised.
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

    /** Latest [SupplyParameters] received from the device, or null if none received yet. */
    var latestSupply: SupplyParameters? = null
        internal set

    /** Latest [DemandParameters] received from the device, or null if none received yet. */
    var latestDemand: DemandParameters? = null
        internal set

    /** Latest [StorageParameters] received from the device, or null if none received yet. */
    var latestStorage: StorageParameters? = null
        internal set

    /** Called after each successful [send]. Set by [EpServer] to fire listener notifications. */
    internal var afterSend: ((Message) -> Unit)? = null

    /** Send a message to this device. */
    suspend fun send(message: Message) {
        transport.send(message)
        afterSend?.invoke(message)
    }

    /**
     * Close the underlying transport and mark this session as [SessionState.CLOSED].
     *
     * Internal — external callers should use [EpServer.disconnect] to ensure
     * proper cleanup (peer unlinking, listener callbacks).
     */
    internal fun close() {
        state = SessionState.CLOSED
        transport.close()
    }
}
