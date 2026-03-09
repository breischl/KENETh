package dev.breischl.keneth.server

import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.TransportListener
import dev.breischl.keneth.transport.tcp.RawTcpServerTransport
import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.SocketException

/**
 * Accepts TCP connections on a given port and feeds them into an [EpNode].
 *
 * Each accepted socket is wrapped in [RawTcpServerTransport] → [MessageTransport]
 * and passed to [EpNode.accept] to start the EP handshake.
 *
 * The server socket is not created until [start] is called, so constructing a
 * `TcpAcceptor` does not bind the port.
 *
 * Example:
 * ```kotlin
 * val node = EpNode(identity = nodeParams, acceptor = TcpAcceptor(port = 56540))
 * node.start() // acceptor.start(node) is called internally
 * // ... node is now listening for connections
 * node.close() // acceptor.close() is called internally
 * ```
 *
 * @param port The TCP port to listen on. Use 0 for an ephemeral port.
 * @param transportListener Optional listener forwarded to each [RawTcpServerTransport].
 */
class TcpAcceptor(
    private val port: Int,
    private val transportListener: TransportListener? = null,
) : InboundAcceptor {

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var _closed = false

    /** The actual port this acceptor is listening on, or null if not started. */
    val localPort: Int? get() = serverSocket?.localPort

    /** Whether this acceptor has been closed. */
    val isClosed: Boolean get() = serverSocket?.isClosed ?: _closed

    /**
     * Starts the accept loop. Binds the server socket and begins accepting connections.
     * Each accepted connection is wrapped and passed to the node.
     */
    override fun start(node: EpNode) {
        check(acceptJob == null) { "Already started" }
        val socket = ServerSocket(port)
        serverSocket = socket
        acceptJob = scope.launch {
            try {
                while (isActive) {
                    val clientSocket = socket.accept()
                    val rawTransport = RawTcpServerTransport(clientSocket, transportListener)
                    val transport = MessageTransport(rawTransport)
                    node.accept(transport)
                }
            } catch (_: SocketException) {
                // Expected when serverSocket is closed — exit loop
            }
        }
    }

    override fun close() {
        _closed = true
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope.cancel()
    }
}
