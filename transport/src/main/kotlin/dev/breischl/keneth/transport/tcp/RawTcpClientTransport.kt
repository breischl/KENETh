package dev.breischl.keneth.transport.tcp

import dev.breischl.keneth.transport.SocketTransport
import java.net.Socket

/**
 * A raw TCP client transport that creates outbound connections.
 *
 * Example usage:
 * ```kotlin
 * val transport = RawTcpClientTransport("localhost", 56540)
 * try {
 *     transport.send(pingFrame)
 *     transport.receive().collect { result ->
 *         println("Received: ${result.value}")
 *     }
 * } finally {
 *     transport.close()
 * }
 * ```
 *
 * @property host The hostname or IP address of the remote endpoint.
 * @property port The port number to connect to (default: [SocketTransport.DEFAULT_PORT]).
 */
class RawTcpClientTransport(
    val host: String,
    val port: Int = DEFAULT_PORT
) : SocketTransport() {

    override fun socket(): Socket {
        synchronized(lock) {
            socket?.let { if (!it.isClosed) return it }
            val newSocket = Socket(host, port)
            socket = newSocket
            return newSocket
        }
    }
}