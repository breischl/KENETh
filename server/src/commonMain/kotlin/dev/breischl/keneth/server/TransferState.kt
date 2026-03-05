package dev.breischl.keneth.server

/**
 * Lifecycle state of an [EnergyTransfer].
 */
enum class TransferState {
    /** The transfer is actively publishing parameters at the configured tick rate. */
    ACTIVE,

    /** The transfer has been stopped (manually or due to peer disconnect/error). */
    STOPPED,
}
