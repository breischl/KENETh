package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.DemandParameters
import dev.breischl.keneth.core.messages.StorageParameters
import dev.breischl.keneth.core.messages.SupplyParameters
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Immutable snapshot of a [Peer]'s state at a point in time.
 *
 * @property peerId The peer's unique ID.
 * @property connectionState Connection state at the time of the snapshot.
 * @property remoteIdentity The identity reported by the remote device, or null if not yet connected.
 * @property latestSupply Latest supply parameters at the time of the snapshot, or null.
 * @property latestDemand Latest demand parameters at the time of the snapshot, or null.
 * @property latestStorage Latest storage parameters at the time of the snapshot, or null.
 * @property timestamp When the snapshot was taken.
 */
data class PeerSnapshot(
    val peerId: String,
    val connectionState: ConnectionState,
    val remoteIdentity: String?,
    val latestSupply: SupplyParameters?,
    val latestDemand: DemandParameters?,
    val latestStorage: StorageParameters?,
    val timestamp: Instant,
)

@OptIn(ExperimentalTime::class)
internal fun Peer.snapshot() = PeerSnapshot(
    peerId = peerId,
    connectionState = connectionState,
    remoteIdentity = remoteIdentity,
    latestSupply = latestSupply,
    latestDemand = latestDemand,
    latestStorage = latestStorage,
    timestamp = Clock.System.now(),
)
