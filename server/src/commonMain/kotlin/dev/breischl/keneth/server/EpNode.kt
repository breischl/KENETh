package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.TransportListener
import dev.breischl.keneth.transport.safeNotify
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * High-level EnergyNet Protocol node.
 *
 * Provides a single entry point that owns an [EpServer] and optional TCP acceptor,
 * delegating peer management and exposing a clean API for connecting to and
 * communicating with EP devices.
 *
 * Example:
 * ```kotlin
 * val node = EpNode(
 *     config = NodeConfig(
 *         identity = SessionParameters(identity = "router-1", type = "router"),
 *         listenPort = 56540,
 *     ),
 * )
 * node.addPeer(PeerConfig(peerId = "charger-1", direction = PeerDirection.INBOUND))
 * node.start()
 * // ... node is now listening and managing peer connections
 * node.close()
 * ```
 *
 * @param config Node configuration including identity and listen port.
 * @param listener Optional callback for peer-level lifecycle events.
 * @param server Injectable [EpServer] for testing. When null, one is created internally
 *   with a bridging [ServerListener] that translates session events into [NodeListener] callbacks.
 * @param coroutineContext Additional coroutine context elements (e.g., a test dispatcher).
 */
class EpNode(
    internal val config: NodeConfig,
    private val listener: NodeListener? = null,
    server: EpServer? = null,
    private val coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AutoCloseable {

    internal val server: EpServer = server ?: EpServer(
        serverParameters = config.identity,
        listener = BridgingServerListener(),
        transportListener = config.transportListener,
        outboundTransportFactory = defaultOutboundFactory(config.transportListener),
        coroutineContext = coroutineContext,
    )

    // Separate scope from EpServer — close() cancels this first so transfer
    // coroutines complete their cleanup while the server is still alive.
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val transfers = mutableMapOf<String, EnergyTransfer>()

    /** Platform-specific acceptor (e.g., TcpAcceptor on JVM, null on JS). */
    internal var tcpAcceptor: AutoCloseable? = null

    /** The actual local port, set by [startPlatformSpecific] when TCP listening starts. */
    internal var _localPort: Int? = null

    /**
     * Start the node.
     *
     * If [NodeConfig.listenPort] is set, creates and starts a platform-specific acceptor
     * to accept inbound connections (e.g., TCP on JVM).
     */
    fun start() {
        startPlatformSpecific()
    }

    override fun close() {
        // Cancel our scope first so transfer coroutines run their finally blocks
        // (including onTransferStopped callbacks) while the server is still alive.
        scope.cancel()
        tcpAcceptor?.close()
        tcpAcceptor = null
        _localPort = null
        server.close()
    }

    /**
     * Add a configured peer.
     *
     * For outbound or bidirectional peers, a connection is initiated immediately.
     *
     * @see EpServer.addPeer
     */
    fun addPeer(config: PeerConfig) {
        server.addPeer(config)
    }

    /**
     * Remove a configured peer.
     *
     * If the peer has an active session, it is disconnected first.
     * Any active transfer for this peer is stopped.
     *
     * @see EpServer.removePeer
     */
    fun removePeer(peerId: String) {
        stopTransfer(peerId)
        server.removePeer(peerId)
    }

    /** Read-only snapshot of all configured peers. */
    val peers: Map<String, Peer> get() = server.peers

    /** The local port the node is listening on, or null if not listening. */
    val localPort: Int? get() = _localPort

    // -- Transfer management --

    /**
     * Start publishing energy parameters to a connected peer.
     *
     * Launches a coroutine that sends each non-null parameter message every [tickRate].
     * The transfer can be updated with [updateTransfer] or stopped with [stopTransfer].
     *
     * Returns a [StartTransferResult] indicating success or the reason for failure.
     *
     * Note: a [StartTransferResult.Success] means the transfer was launched, but the
     * peer could disconnect between the state check and the first tick. In that case
     * the transfer stops immediately and [NodeListener.onTransferStopped] fires.
     * Callers should handle this via the listener rather than assuming Success
     * guarantees sustained publishing.
     *
     * @param peerId The peer to send parameters to.
     * @param params The parameters to publish.
     * @param tickRate How often to send parameters. Defaults to 100ms.
     */
    fun startTransfer(
        peerId: String,
        params: TransferParams,
        tickRate: Duration = 100.milliseconds
    ): StartTransferResult {
        val peer = server.peers[peerId]
            ?: return StartTransferResult.PeerNotFound(peerId)
        if (peer.connectionState != ConnectionState.CONNECTED) {
            return StartTransferResult.PeerNotConnected(peerId, peer.connectionState)
        }
        if (transfers.containsKey(peerId)) {
            return StartTransferResult.TransferAlreadyActive(peerId)
        }

        val transfer = EnergyTransfer(peerId = peerId, _params = params)
        transfers[peerId] = transfer

        transfer.job = scope.launch {
            try {
                while (isActive) {
                    val session = peer.session
                    if (session == null || session.state != SessionState.ACTIVE) break

                    val currentParams = transfer._params
                    currentParams.supply?.let { session.send(it) }
                    currentParams.demand?.let { session.send(it) }
                    currentParams.storage?.let { session.send(it) }

                    delay(tickRate)
                }
            } catch (_: CancellationException) {
                // Normal shutdown
            } catch (_: Exception) {
                // Transport error — stop transfer
            } finally {
                transfer._state = TransferState.STOPPED
                transfers.remove(peerId)
                listener.safeNotify { onTransferStopped(transfer.snapshot()) }
            }
        }

        listener.safeNotify { onTransferStarted(transfer.snapshot()) }
        return StartTransferResult.Success(transfer)
    }

    /**
     * Update the parameters for an active transfer.
     *
     * The new parameters take effect on the next tick.
     *
     * @throws IllegalStateException if no active transfer exists for this peer.
     */
    fun updateTransfer(peerId: String, params: TransferParams) {
        val transfer = transfers[peerId]
            ?: throw IllegalStateException("No active transfer for peer '$peerId'")
        transfer._params = params
    }

    /**
     * Stop an active transfer.
     *
     * Cancels the publishing coroutine and marks the transfer as [TransferState.STOPPED].
     * No-op if no transfer is active for this peer.
     */
    fun stopTransfer(peerId: String) {
        val transfer = transfers[peerId] ?: return
        transfer.job?.cancel()
    }

    /**
     * Bridges [ServerListener] events to [NodeListener] callbacks.
     *
     * Translates low-level session events into the higher-level peer-focused API:
     * - Peer connect/disconnect → [NodeListener.onPeerConnected] / [NodeListener.onPeerDisconnected]
     * - Supply/Demand/Storage messages → [NodeListener.onPeerParametersUpdated]
     * - Sent messages → [NodeListener.onMessageSent]
     * - Session errors → [NodeListener.onError]
     */
    private inner class BridgingServerListener : ServerListener {
        override fun onPeerConnected(peer: PeerSnapshot) {
            listener.safeNotify { onPeerConnected(peer) }
        }

        override fun onPeerDisconnected(peer: PeerSnapshot) {
            stopTransfer(peer.peerId)
            listener.safeNotify { onPeerDisconnected(peer) }
        }

        override fun onMessageReceived(session: DeviceSessionSnapshot, message: Message) {
            if (message is SupplyParameters || message is DemandParameters || message is StorageParameters) {
                val peer = server.peerForSession(session.id)
                if (peer != null) {
                    listener.safeNotify { onPeerParametersUpdated(peer.snapshot(), message) }
                }
            }
        }

        override fun onMessageSent(session: DeviceSessionSnapshot, message: Message) {
            val peer = server.peerForSession(session.id)
            if (peer != null) {
                listener.safeNotify { onMessageSent(peer.snapshot(), message) }
            }
        }

        override fun onSessionError(session: DeviceSessionSnapshot, error: Throwable) {
            listener.safeNotify { onError(error) }
        }
    }
}

internal expect fun EpNode.startPlatformSpecific()
internal expect fun defaultOutboundFactory(listener: TransportListener?): (suspend (String, Int) -> MessageTransport)?
