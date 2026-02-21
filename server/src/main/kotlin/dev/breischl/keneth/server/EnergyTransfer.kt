package dev.breischl.keneth.server

import kotlinx.coroutines.Job

/**
 * Represents an active or stopped energy parameter transfer to a peer.
 *
 * Created via [EpNode.startTransfer] and managed via [EpNode.updateTransfer]
 * and [EpNode.stopTransfer]. This class provides a read-only view of the
 * transfer state; mutation is performed through `EpNode` methods.
 *
 * @property peerId The peer this transfer is associated with.
 */
class EnergyTransfer internal constructor(
    val peerId: String,
    @Volatile internal var _params: TransferParams,
    @Volatile internal var _state: TransferState = TransferState.ACTIVE,
    internal var job: Job? = null,
) {
    /** Current state of this transfer. */
    val state: TransferState get() = _state

    /** Current parameters being published. */
    val params: TransferParams get() = _params
}
