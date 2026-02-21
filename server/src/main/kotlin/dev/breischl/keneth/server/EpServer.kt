package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.TransportListener
import dev.breischl.keneth.transport.tcp.RawTcpClientTransport
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Low-level EnergyNet Protocol server that manages device sessions.
 *
 * Accepts [MessageTransport] connections via [accept], enforces the EP
 * handshake protocol (both sides exchange [SessionParameters]), dispatches
 * incoming messages, and tracks per-device state.
 *
 * This class does NOT listen on a socket — callers provide already-established
 * transports. This decouples the server from the network layer and makes it
 * testable with fake transports.
 *
 * Example:
 * ```kotlin
 * val server = EpServer(
 *     serverParameters = SessionParameters(identity = "router-1", type = "router"),
 * )
 *
 * // Accept a connection (typically from a ServerSocket.accept() loop)
 * val transport = MessageTransport(RawTcpServerTransport(socket))
 * val session = server.accept(transport)
 * ```
 *
 * @param serverParameters Our identity, sent to each device during handshake.
 * @param listener Optional callback for session lifecycle events.
 * @param transportListener Optional listener forwarded to outbound transports.
 * @param coroutineContext Additional coroutine context elements (e.g., a test dispatcher).
 *   A [SupervisorJob] is always added so that individual session failures are isolated.
 */
class EpServer(
    private val serverParameters: SessionParameters,
    private val listener: ServerListener? = null,
    private val transportListener: TransportListener? = null,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : Closeable {

    private val _sessions = ConcurrentHashMap<String, DeviceSession>()
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val _peers = ConcurrentHashMap<String, Peer>()
    private val sessionToPeer = ConcurrentHashMap<String, Peer>()

    /** Read-only snapshot of all sessions currently tracked by this server. */
    val sessions: Map<String, DeviceSession> get() = _sessions.toMap()

    /** Read-only snapshot of all configured peers. */
    val peers: Map<String, Peer> get() = _peers.toMap()

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
    fun accept(transport: MessageTransport): DeviceSession {
        val session = DeviceSession(
            id = UUID.randomUUID().toString(),
            transport = transport,
        )
        _sessions[session.id] = session
        listener.safeNotify { onSessionCreated(session) }
        scope.launch { runSession(session) }
        return session
    }

    /**
     * Add a configured peer.
     *
     * For [PeerDirection.OUTBOUND] or [PeerDirection.BIDIRECTIONAL] peers, an outbound
     * connection is initiated immediately.
     *
     * @throws IllegalArgumentException if [PeerConfig.host] is null for an outbound peer,
     *   or if a peer with the same ID already exists.
     */
    fun addPeer(config: PeerConfig) {
        require(config.direction == PeerDirection.INBOUND || config.host != null) {
            "host is required for OUTBOUND and BIDIRECTIONAL peers"
        }
        val peer = Peer(config)
        require(_peers.putIfAbsent(config.peerId, peer) == null) {
            "Peer '${config.peerId}' already exists"
        }
        if (config.direction != PeerDirection.INBOUND) {
            connectOutbound(peer)
        }
    }

    /**
     * Remove a configured peer.
     *
     * If the peer has an active session, it is disconnected first.
     */
    fun removePeer(peerId: String) {
        val peer = _peers.remove(peerId) ?: return
        val session = peer.session
        if (session != null) {
            sessionToPeer.remove(session.id)
            peer.session = null
            closeSession(session)
        }
    }

    private fun connectOutbound(peer: Peer) {
        val host = peer.config.host ?: return
        scope.launch {
            try {
                val rawTransport = RawTcpClientTransport(host, peer.config.port, transportListener)
                val transport = MessageTransport(rawTransport)
                val session = accept(transport)
                peer.session = session
                sessionToPeer[session.id] = peer
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Connection failed — peer stays DISCONNECTED
            }
        }
    }

    /**
     * Gracefully disconnect a session.
     *
     * Sends a [SoftDisconnect] message to the device (if the session is active)
     * and closes the session.
     */
    suspend fun disconnect(session: DeviceSession, reason: String? = null) {
        if (session.state == SessionState.ACTIVE) {
            session.state = SessionState.DISCONNECTING
            try {
                session.send(SoftDisconnect(reconnect = false, reason = reason))
            } catch (_: Exception) {
                // Transport may already be broken
            }
            listener.safeNotify { onSessionDisconnecting(session, null) }
        }
        closeSession(session)
    }

    override fun close() {
        _sessions.values.toList().forEach { closeSession(it) }
        scope.cancel()
    }

    private suspend fun runSession(session: DeviceSession) {
        try {
            session.transport.receive().collect { received ->
                if (!received.succeeded) return@collect

                val message = received.message!!

                when (session.state) {
                    SessionState.AWAITING_SESSION -> handleHandshake(session, message)
                    SessionState.ACTIVE -> handleActiveMessage(session, message)
                    SessionState.DISCONNECTING, SessionState.CLOSED -> { /* ignore */
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            listener.safeNotify { onSessionError(session, e) }
        } finally {
            closeSession(session)
        }
    }

    private suspend fun handleHandshake(session: DeviceSession, message: Message) {
        if (message !is SessionParameters) {
            listener.safeNotify {
                onSessionHandshakeFailed(
                    session,
                    "Expected SessionParameters, got ${message::class.simpleName}"
                )
            }
            closeSession(session)
            return
        }
        session.sessionParameters = message
        session.state = SessionState.ACTIVE
        session.send(serverParameters)
        listener.safeNotify { onSessionActive(session) }

        // Link inbound sessions to configured peers by identity
        val peer = linkInboundPeer(session, message.identity)
        if (peer != null) {
            listener.safeNotify { onPeerConnected(peer) }
        } else {
            // Check if this session was already linked by connectOutbound
            val outboundPeer = sessionToPeer[session.id]
            if (outboundPeer != null) {
                listener.safeNotify { onPeerConnected(outboundPeer) }
            }
        }
    }

    /** Tries to match an inbound session to a configured peer by identity. */
    private fun linkInboundPeer(session: DeviceSession, identity: String): Peer? {
        if (sessionToPeer.containsKey(session.id)) return null // already linked (outbound)
        val peer = _peers.values.firstOrNull { peer ->
            peer.config.direction != PeerDirection.OUTBOUND &&
                    peer.config.resolvedExpectedIdentity == identity &&
                    peer.session == null
        } ?: return null
        peer.session = session
        sessionToPeer[session.id] = peer
        return peer
    }

    private fun handleActiveMessage(session: DeviceSession, message: Message) {
        when (message) {
            is SupplyParameters -> session.latestSupply = message
            is DemandParameters -> session.latestDemand = message
            is StorageParameters -> session.latestStorage = message
            is Ping -> { /* keep-alive, nothing to update */
            }

            is SoftDisconnect -> {
                session.state = SessionState.DISCONNECTING
                listener.safeNotify { onSessionDisconnecting(session, message) }
            }

            else -> { /* unknown message types — tracked via listener */
            }
        }
        listener.safeNotify { onMessageReceived(session, message) }
    }

    private fun closeSession(session: DeviceSession) {
        if (session.state == SessionState.CLOSED) return
        session.close()
        _sessions.remove(session.id)
        val peer = sessionToPeer.remove(session.id)
        if (peer != null) {
            peer.session = null
            listener.safeNotify { onPeerDisconnected(peer) }
        }
        listener.safeNotify { onSessionClosed(session) }
    }
}
