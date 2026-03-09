package dev.breischl.keneth.server

import kotlinx.coroutines.Job

/**
 * Represents an active or stopped energy parameter transfer to a peer.
 *
 * Created via [EpNode.startTransfer] and managed via [EpNode.stopTransfer].
 * This class provides a read-only view of the transfer state.
 *
 * @property peerId The peer this transfer is associated with.
 */
class EnergyTransfer internal constructor(
    val peerId: String,
    @kotlin.concurrent.Volatile internal var _state: TransferState = TransferState.ACTIVE,
    internal var job: Job? = null,
) {
    /** Current state of this transfer. */
    val state: TransferState get() = _state
}
