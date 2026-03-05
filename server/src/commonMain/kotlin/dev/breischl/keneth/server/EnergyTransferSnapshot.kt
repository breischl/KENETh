package dev.breischl.keneth.server

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Immutable snapshot of an [EnergyTransfer]'s state at a point in time.
 *
 * @property peerId The peer this transfer is associated with.
 * @property state Transfer state at the time of the snapshot.
 * @property params Transfer parameters at the time of the snapshot.
 * @property timestamp When the snapshot was taken.
 */
data class EnergyTransferSnapshot(
    val peerId: String,
    val state: TransferState,
    val params: TransferParams,
    val timestamp: Instant,
)

@OptIn(ExperimentalTime::class)
internal fun EnergyTransfer.snapshot() = EnergyTransferSnapshot(
    peerId = peerId,
    state = state,
    params = params,
    timestamp = Clock.System.now(),
)
