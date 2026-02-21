package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.DemandParameters
import dev.breischl.keneth.core.messages.StorageParameters
import dev.breischl.keneth.core.messages.SupplyParameters

/**
 * Read-only view of a peer's current state.
 *
 * Peers are configured via [PeerConfig] and managed by [EpServer]. The connection
 * state is derived from the linked [DeviceSession], if any.
 */
class Peer internal constructor(
    val config: PeerConfig,
    internal var session: DeviceSession? = null,
) {
    val peerId: String get() = config.peerId

    /** Current connection state, derived from the linked session. */
    val connectionState: ConnectionState
        get() {
            val s = session ?: return ConnectionState.DISCONNECTED
            return when (s.state) {
                SessionState.AWAITING_SESSION -> ConnectionState.CONNECTING
                SessionState.ACTIVE -> ConnectionState.CONNECTED
                SessionState.DISCONNECTING, SessionState.CLOSED -> ConnectionState.DISCONNECTED
            }
        }

    /** The identity reported by the remote device, or null if not yet connected. */
    val remoteIdentity: String? get() = session?.sessionParameters?.identity

    /** Latest [SupplyParameters] received from this peer's device. */
    val latestSupply: SupplyParameters? get() = session?.latestSupply

    /** Latest [DemandParameters] received from this peer's device. */
    val latestDemand: DemandParameters? get() = session?.latestDemand

    /** Latest [StorageParameters] received from this peer's device. */
    val latestStorage: StorageParameters? get() = session?.latestStorage
}
