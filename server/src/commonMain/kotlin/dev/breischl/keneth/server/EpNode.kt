@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.SoftDisconnect
import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.TransportListener
import dev.breischl.keneth.transport.safeNotify
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * [EpNode] can be loosely thought of as the "brain stem" of an EnergyNet node.
 *
 * It handles low-level autonomic functions like receiving and opening connections,
 * listening for remote messages, and sending messages when told to. However, it lacks any higher-level behavior -
 * user code must implement that by registering for listener callbacks on received messages, reacting to those messages,
 * and directing [EpNode] to send messages in response.
 *
 * Example:
 * ```kotlin
 * val node = EpNode(
 *     identity = SessionParameters(identity = "router-1", type = "router"),
 *     acceptor = TcpAcceptor(port = 56540),
 * )
 * node.addPeer(PeerConfig.Inbound(peerId = "charger-1"))
 * node.start()
 * // ... node is now listening and managing peer connections
 * node.close()
 * ```
 *
 * @param identity The node's identity, sent to each peer during the EP handshake.
 * @param acceptor Strategy for accepting inbound connections, or null to disable listening.
 * @param transportListener Optional listener for transport-level events (frame/message I/O).
 * @param nodeListener Optional callback for session and peer lifecycle events.
 * @param transferReceiveTimeout Closes the session if no message is received within this
 *   window while an energy transfer is active. EP Spec section 5.1 specifies this must be no more than 200 ms.
 *   Defaults to 200 ms (EP minimum: 5 Hz).
 * @param idleReceiveTimeout Closes the session if no message is received within this
 *   window when no transfer is active. Defaults to 5 s.
 * @param coroutineContext Additional coroutine context elements (e.g., a test dispatcher).
 */
class EpNode(
    val identity: SessionParameters,
    val acceptor: InboundAcceptor? = null,
    val transportListener: TransportListener? = null,
    private val nodeListener: NodeListener? = null,
    val transferReceiveTimeout: Duration = 200.milliseconds,
    val idleReceiveTimeout: Duration = 5.seconds,
    private val coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AutoCloseable {

    private val _sessions = mutableMapOf<String, DeviceSession>()
    private val _peers = mutableMapOf<String, Peer>()
    private val sessionToPeer = mutableMapOf<String, Peer>()

    // sessionScope drives all session coroutines.
    private val sessionScope = CoroutineScope(SupervisorJob() + coroutineContext)

    // transferScope is cancelled first in close() so transfer coroutines run their
    // finally blocks (state update + cleanup) while sessions are still alive.
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
        nodeListener.safeNotify { onSessionCreated(session.snapshot(peerId = null)) }
        sessionScope.launch { runSession(session) }
        return session
    }

    /**
     * Start the node.
     *
     * If [acceptor] is set, starts it to begin accepting inbound connections.
     */
    fun start() {
        acceptor?.start(this)
    }

    override fun close() {
        // Cancel transfers first so their finally blocks run while sessions are still alive.
        transferScope.cancel()
        acceptor?.close()
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
            nodeListener.safeNotify { onSessionDisconnecting(session.snapshot(peerId = peer?.peerId), null) }
        }
        closeSession(session)
    }

    // -- Transfer management --

    /**
     * Start publishing energy parameters to a connected peer.
     *
     * Launches a coroutine that calls [paramsProvider] on each tick and sends
     * any non-null messages from the returned [TransferParams]. The caller
     * controls what is sent by mutating the state that [paramsProvider] captures.
     * The transfer can be stopped with [stopTransfer].
     *
     * Returns a [StartTransferResult] indicating success or the reason for failure.
     *
     * Note: a [StartTransferResult.Success] means the transfer was launched, but the
     * peer could disconnect between the state check and the first tick, in which case
     * the transfer stops immediately.
     *
     * @param peerId The peer to send parameters to.
     * @param paramsProvider Called each tick to obtain the parameters to send. Any field that is
     *   non-null on the first tick must remain non-null for the lifetime of the transfer; see
     *   [TransferParams] for details.
     * @param tickRate How often to send parameters. Defaults to 100ms. EnergyNet spec requires no more than 200ms or remote side will cancel transfer.
     */
    fun startTransfer(
        peerId: String,
        paramsProvider: () -> TransferParams,
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

        val transfer = EnergyTransfer(peerId = peerId)
        transfers[peerId] = transfer
        // Signal the session watchdog to re-evaluate its timeout with the tighter transfer window.
        peer.session?.receiveHeartbeat?.trySend(Unit)

        transfer.job = transferScope.launch {
            try {
                while (isActive) {
                    val session = peer.session
                    if (session == null || session.state != SessionState.ACTIVE) break

                    val currentParams = paramsProvider()
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
            }
        }

        return StartTransferResult.Success(transfer)
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

    /** Thrown internally by the watchdog to trigger session close on receive timeout. */
    private class ReceiveTimeoutException : CancellationException("EP receive timeout")

    /** Returns the receive timeout that applies to [session] given current transfer state. */
    private fun activeReceiveTimeout(session: DeviceSession): Duration {
        val peer = peerForSession(session.id)
        return if (peer != null && transfers.containsKey(peer.peerId)) {
            transferReceiveTimeout
        } else {
            idleReceiveTimeout
        }
    }

    private fun connectOutbound(peer: Peer) {
        val peerConfig = peer.config as? PeerConfig.Outbound ?: return
        sessionScope.launch {
            try {
                val transport = peerConfig.connector.connect(transportListener)
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
                nodeListener.safeNotify { onSessionCreated(session.snapshot(peerId = peer.peerId)) }
                // Outbound side initiates the EP handshake by sending our identity first.
                session.send(identity)
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
            nodeListener.safeNotify { onMessageSent(session.snapshot(peerId = peer?.peerId), message) }
        }
    }

    private suspend fun runSession(session: DeviceSession) {
        try {
            coroutineScope {
                val runScope = this

                // Watchdog: cancel this scope if no heartbeat is received within the timeout.
                // Uses withTimeoutOrNull (backed by delay) so it works with virtual time in tests.
                val watchdogJob = launch {
                    while (isActive) {
                        val timeout = activeReceiveTimeout(session)
                        val beat = withTimeoutOrNull(timeout) { session.receiveHeartbeat.receive() }
                        if (beat == null) {
                            // No message within the deadline — treat connection as lost.
                            runScope.cancel(ReceiveTimeoutException())
                            return@launch
                        }
                        // Heartbeat received; loop to start a fresh wait with the current timeout.
                    }
                }

                session.transport.receive().collect { received ->
                    session.receiveHeartbeat.trySend(Unit)
                    if (!received.succeeded) return@collect

                    val message = received.message!!

                    when (session.state) {
                        SessionState.AWAITING_SESSION -> handleHandshake(session, message)
                        SessionState.ACTIVE -> handleActiveMessage(session, message)
                        SessionState.DISCONNECTING, SessionState.CLOSED -> { /* ignore */ }
                    }
                }
                // Transport closed normally — stop the watchdog so the scope can complete.
                watchdogJob.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val peer = peerForSession(session.id)
            nodeListener.safeNotify { onSessionError(session.snapshot(peerId = peer?.peerId), e) }
        } finally {
            closeSession(session)
        }
    }

    private suspend fun handleHandshake(session: DeviceSession, message: Message) {
        if (message !is SessionParameters) {
            nodeListener.safeNotify {
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
        session.send(identity)
        nodeListener.safeNotify { onSessionActive(session.snapshot(peerId = null)) }

        // Link inbound sessions to configured peers by identity
        val peer = linkInboundPeer(session, message.identity)
        if (peer != null) {
            nodeListener.safeNotify { onPeerConnected(session.snapshot(peerId = peer.peerId)) }
        } else {
            // Check if this session was already linked by connectOutbound
            val outboundPeer = sessionToPeer[session.id]
            if (outboundPeer != null) {
                nodeListener.safeNotify { onPeerConnected(session.snapshot(peerId = outboundPeer.peerId)) }
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
            nodeListener.safeNotify { onSessionDisconnecting(session.snapshot(peerId = peer?.peerId), message) }
        }

        nodeListener.safeNotify { onMessageReceived(session.snapshot(peerId = peer?.peerId), message) }
    }

    private fun closeSession(session: DeviceSession) {
        if (session.state == SessionState.CLOSED) return
        val peer = sessionToPeer.remove(session.id)
        session.close()
        _sessions.remove(session.id)
        if (peer != null) {
            peer.session = null
            stopTransfer(peer.peerId)
            nodeListener.safeNotify { onPeerDisconnected(session.snapshot(peerId = peer.peerId)) }
        }
        nodeListener.safeNotify { onSessionClosed(session.snapshot(peerId = peer?.peerId)) }
    }
}
