package dev.breischl.keneth.server

/**
 * Result of attempting to start an energy transfer via [EpNode.startTransfer].
 *
 * Use a `when` expression to handle all cases:
 * ```kotlin
 * when (val result = node.startTransfer("charger-1", params)) {
 *     is StartTransferResult.Success -> println("Transfer started: ${result.transfer.peerId}")
 *     is StartTransferResult.PeerNotFound -> println("Unknown peer: ${result.peerId}")
 *     is StartTransferResult.PeerNotConnected -> println("Peer not connected: ${result.peerId}")
 *     is StartTransferResult.TransferAlreadyActive -> println("Already transferring")
 * }
 * ```
 */
sealed class StartTransferResult {
    /** Transfer started successfully. */
    data class Success(val transfer: EnergyTransfer) : StartTransferResult()

    /** No peer with this ID is configured. */
    data class PeerNotFound(val peerId: String) : StartTransferResult()

    /** The peer exists but has not completed the EP handshake. */
    data class PeerNotConnected(val peerId: String) : StartTransferResult()

    /** A transfer is already active for this peer. */
    data class TransferAlreadyActive(val peerId: String) : StartTransferResult()
}
