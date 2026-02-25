package dev.breischl.keneth.server

import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.TransportListener
import dev.breischl.keneth.transport.tcp.RawTcpServerTransport
import kotlinx.coroutines.*
import java.io.Closeable
import java.net.ServerSocket
import java.net.SocketException

/**
 * Accepts TCP connections on a given port and feeds them into an [EpServer].
 *
 * Each accepted socket is wrapped in [RawTcpServerTransport] → [MessageTransport]
 * and passed to [EpServer.accept] to start the EP handshake.
 *
 * The server socket is not created until [start] is called, so constructing a
 * `TcpAcceptor` does not bind the port.
 *
 * Example:
 * ```kotlin
 * val server = EpServer(serverParams)
 * val acceptor = TcpAcceptor(server, port = 56540)
 * acceptor.start()
 * // ... acceptor is now listening for connections
 * acceptor.close() // stops accepting
 * ```
 *
 * @param server The EP server to feed accepted connections into.
 * @param port The TCP port to listen on. Use 0 for an ephemeral port.
 * @param transportListener Optional listener forwarded to each [RawTcpServerTransport].
 */
class TcpAcceptor(
    private val server: EpServer,
    private val port: Int,
    private val transportListener: TransportListener? = null,
) : Closeable {

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    /** The actual port this acceptor is listening on, or null if not started. */
    val localPort: Int? get() = serverSocket?.localPort

    /** Whether this acceptor has been closed. */
    val isClosed: Boolean get() = serverSocket?.isClosed ?: (acceptJob != null)

    /**
     * Starts the accept loop. Binds the server socket and begins accepting connections.
     * Each accepted connection is wrapped and passed to the server.
     */
    fun start() {
        check(acceptJob == null) { "Already started" }
        val socket = ServerSocket(port)
        serverSocket = socket
        acceptJob = scope.launch {
            try {
                while (isActive) {
                    val clientSocket = socket.accept()
                    val rawTransport = RawTcpServerTransport(clientSocket, transportListener)
                    val transport = MessageTransport(rawTransport)
                    server.accept(transport)
                }
            } catch (_: SocketException) {
                // Expected when serverSocket is closed — exit loop
            }
        }
    }

    override fun close() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope.cancel()
    }
}
