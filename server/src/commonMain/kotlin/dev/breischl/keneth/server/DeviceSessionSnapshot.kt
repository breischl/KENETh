package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.DemandParameters
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.StorageParameters
import dev.breischl.keneth.core.messages.SupplyParameters
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Immutable snapshot of a [DeviceSession]'s state at a point in time.
 *
 * This is a pure observation value; it does not support sending messages.
 *
 * @property id The session's unique ID.
 * @property sessionParameters Device identity from the handshake, or null if not yet completed.
 * @property state Session lifecycle state at the time of the snapshot.
 * @property latestSupply Latest supply parameters at the time of the snapshot, or null.
 * @property latestDemand Latest demand parameters at the time of the snapshot, or null.
 * @property latestStorage Latest storage parameters at the time of the snapshot, or null.
 * @property timestamp When the snapshot was taken.
 */
data class DeviceSessionSnapshot(
    val id: String,
    val sessionParameters: SessionParameters?,
    val state: SessionState,
    val latestSupply: SupplyParameters?,
    val latestDemand: DemandParameters?,
    val latestStorage: StorageParameters?,
    val timestamp: Instant,
)

@OptIn(ExperimentalTime::class)
internal fun DeviceSession.snapshot() = DeviceSessionSnapshot(
    id = id,
    sessionParameters = sessionParameters,
    state = state,
    latestSupply = latestSupply,
    latestDemand = latestDemand,
    latestStorage = latestStorage,
    timestamp = Clock.System.now(),
)
