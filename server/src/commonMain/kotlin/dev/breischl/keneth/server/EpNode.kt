@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.SoftDisconnect
import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.safeNotify
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * High-level EnergyNet Protocol node.
 *
 * Manages device sessions, peer lifecycle, and energy transfers in one place.
 * Accepts [MessageTransport] connections (via [accept] or an [InboundAcceptor]),
 * enforces the EP handshake, dispatches messages, and tracks per-device state.
 *
 * Example:
 * ```kotlin
 * val node = EpNode(
 *     config = NodeConfig(
 *         identity = SessionParameters(identity = "router-1", type = "router"),
 *         acceptor = TcpAcceptor(port = 56540),
 *     ),
 * )
 * node.addPeer(PeerConfig.Inbound(peerId = "charger-1"))
 * node.start()
 * // ... node is now listening and managing peer connections
 * node.close()
 * ```
 *
 * @param config Node configuration including identity and optional inbound acceptor.
 * @param listener Optional callback for session and peer lifecycle events.
 * @param coroutineContext Additional coroutine context elements (e.g., a test dispatcher).
 */
class EpNode(
    internal val config: NodeConfig,
    private val listener: NodeListener? = null,
    private val coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AutoCloseable {

    private val _sessions = mutableMapOf<String, DeviceSession>()
    private val _peers = mutableMapOf<String, Peer>()
    private val sessionToPeer = mutableMapOf<String, Peer>()

    // sessionScope drives all session coroutines.
    private val sessionScope = CoroutineScope(SupervisorJob() + coroutineContext)

    // transferScope is cancelled first in close() so transfer coroutines can run their
    // finally blocks (including onTransferStopped callbacks) while sessions are still alive.
    private val transferScope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val transfers = mutableMapOf<String, EnergyTransfer>()

    /** Read-only snapshot of all sessions currently tracked by this node. */
    internal val sessions: Map<String, DeviceSession> get() = _sessions.toMap()

    /** Read-only snapshot of all configured peers. */
    val peers: Map<String, Peer> get() = _peers.toMap()

    /** Returns the peer linked to the given session ID, or null if none. */
    internal fun peerForSession(sessionId: String): Peer? = sessionToPeer[sessionId]

    /**
     * Accept a new connection.
     *
     * Creates a [DeviceSession] in [SessionState.AWAITING_SESSION] state and
     * launches a coroutine to process the session lifecycle. Returns the
     * session immediately.
     *
     * @param transport The already-established message transport for this connection.
     * @return The new session (initially in [SessionState.AWAITING_SESSION]).
     */
    internal fun accept(transport: MessageTransport): DeviceSession {
        val session = DeviceSession(
            id = Uuid.random().toString(),
            transport = transport,
        )
        wireAfterSend(session)
        _sessions[session.id] = session
        listener.safeNotify { onSessionCreated(session.snapshot(peerId = null)) }
        sessionScope.launch { runSession(session) }
        return session
    }

    /**
     * Start the node.
     *
     * If [NodeConfig.acceptor] is set, starts it to begin accepting inbound connections.
     */
    fun start() {
        config.acceptor?.start(this)
    }

    override fun close() {
        // Cancel transfers first so their finally blocks run while sessions are still alive.
        transferScope.cancel()
        config.acceptor?.close()
        _sessions.values.toList().forEach { closeSession(it) }
        sessionScope.cancel()
    }

    /**
     * Add a configured peer.
     *
     * For [PeerConfig.Outbound] peers, a connection is initiated immediately.
     *
     * @throws IllegalArgumentException if a peer with the same ID already exists.
     */
    fun addPeer(config: PeerConfig) {
        val peer = Peer(config)
        require(!_peers.containsKey(config.peerId)) {
            "Peer '${config.peerId}' already exists"
        }
        _peers[config.peerId] = peer
        if (config is PeerConfig.Outbound) {
            connectOutbound(peer)
        }
    }

    /**
     * Remove a configured peer.
     *
     * If the peer has an active session, it is disconnected first.
     * Any active transfer for this peer is stopped.
     */
    fun removePeer(peerId: String) {
        stopTransfer(peerId)
        val peer = _peers.remove(peerId) ?: return
        val session = peer.session
        if (session != null) {
            closeSession(session)
        }
    }

    /**
     * Gracefully disconnect a session.
     *
     * Sends a [SoftDisconnect] message to the device (if the session is active)
     * and closes the session.
     */
    suspend fun disconnect(session: DeviceSession, reason: String? = null) {
        val peer = peerForSession(session.id)
        if (session.state == SessionState.ACTIVE) {
            session.state = SessionState.DISCONNECTING
            try {
                session.send(SoftDisconnect(reconnect = false, reason = reason))
            } catch (_: Exception) {
                // Transport may already be broken
            }
            listener.safeNotify { onSessionDisconnecting(session.snapshot(peerId = peer?.peerId), null) }
        }
        closeSession(session)
    }

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
        val peer = _peers[peerId]
            ?: return StartTransferResult.PeerNotFound(peerId)
        if (!peer.isConnected) {
            return StartTransferResult.PeerNotConnected(peerId)
        }
        if (transfers.containsKey(peerId)) {
            return StartTransferResult.TransferAlreadyActive(peerId)
        }

        val transfer = EnergyTransfer(peerId = peerId, _params = params)
        transfers[peerId] = transfer

        transfer.job = transferScope.launch {
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

    // -- Private session machinery --

    private fun connectOutbound(peer: Peer) {
        val peerConfig = peer.config as? PeerConfig.Outbound ?: return
        sessionScope.launch {
            try {
                val transport = peerConfig.connector.connect(config.transportListener)
                // Create the session and link the peer BEFORE launching runSession,
                // so the handshake code can find the peer even with eager dispatchers.
                val session = DeviceSession(
                    id = Uuid.random().toString(),
                    transport = transport,
                )
                wireAfterSend(session)
                peer.session = session
                sessionToPeer[session.id] = peer
                _sessions[session.id] = session
                listener.safeNotify { onSessionCreated(session.snapshot(peerId = peer.peerId)) }
                // Outbound side initiates the EP handshake by sending our identity first.
                session.send(config.identity)
                runSession(session)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Connection failed — peer stays DISCONNECTED
            }
        }
    }

    /** Wires the [DeviceSession.afterSend] hook to fire [NodeListener.onMessageSent]. */
    private fun wireAfterSend(session: DeviceSession) {
        session.afterSend = { message ->
            val peer = peerForSession(session.id)
            listener.safeNotify { onMessageSent(session.snapshot(peerId = peer?.peerId), message) }
        }
    }

    private suspend fun runSession(session: DeviceSession) {
        try {
            session.transport.receive().collect { received ->
                if (!received.succeeded) return@collect

                val message = received.message!!

                when (session.state) {
                    SessionState.AWAITING_SESSION -> handleHandshake(session, message)
                    SessionState.ACTIVE -> handleActiveMessage(session, message)
                    SessionState.DISCONNECTING, SessionState.CLOSED -> { /* ignore */ }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val peer = peerForSession(session.id)
            listener.safeNotify { onSessionError(session.snapshot(peerId = peer?.peerId), e) }
        } finally {
            closeSession(session)
        }
    }

    private suspend fun handleHandshake(session: DeviceSession, message: Message) {
        if (message !is SessionParameters) {
            listener.safeNotify {
                onSessionHandshakeFailed(
                    session.snapshot(peerId = null),
                    "Expected SessionParameters, got ${message::class.simpleName}"
                )
            }
            closeSession(session)
            return
        }
        session.sessionParameters = message
        session.state = SessionState.ACTIVE
        session.send(config.identity)
        listener.safeNotify { onSessionActive(session.snapshot(peerId = null)) }

        // Link inbound sessions to configured peers by identity
        val peer = linkInboundPeer(session, message.identity)
        if (peer != null) {
            listener.safeNotify { onPeerConnected(session.snapshot(peerId = peer.peerId)) }
        } else {
            // Check if this session was already linked by connectOutbound
            val outboundPeer = sessionToPeer[session.id]
            if (outboundPeer != null) {
                listener.safeNotify { onPeerConnected(session.snapshot(peerId = outboundPeer.peerId)) }
            }
        }
    }

    /** Tries to match an inbound session to a configured peer by identity. */
    private fun linkInboundPeer(session: DeviceSession, identity: String): Peer? {
        if (sessionToPeer.containsKey(session.id)) return null // already linked (outbound)
        val peer = _peers.values.firstOrNull { peer ->
            val config = peer.config
            val acceptsInbound = config is PeerConfig.Inbound ||
                    (config is PeerConfig.Outbound && config.acceptInbound)
            acceptsInbound && config.resolvedExpectedIdentity == identity && peer.session == null
        } ?: return null
        peer.session = session
        sessionToPeer[session.id] = peer
        return peer
    }

    private fun handleActiveMessage(session: DeviceSession, message: Message) {
        val peer = peerForSession(session.id)
        if (message is SoftDisconnect) {
            session.state = SessionState.DISCONNECTING
            listener.safeNotify { onSessionDisconnecting(session.snapshot(peerId = peer?.peerId), message) }
        }

        listener.safeNotify { onMessageReceived(session.snapshot(peerId = peer?.peerId), message) }
    }

    private fun closeSession(session: DeviceSession) {
        if (session.state == SessionState.CLOSED) return
        val peer = sessionToPeer.remove(session.id)
        session.close()
        _sessions.remove(session.id)
        if (peer != null) {
            peer.session = null
            stopTransfer(peer.peerId)
            listener.safeNotify { onPeerDisconnected(session.snapshot(peerId = peer.peerId)) }
        }
        listener.safeNotify { onSessionClosed(session.snapshot(peerId = peer?.peerId)) }
    }
}
