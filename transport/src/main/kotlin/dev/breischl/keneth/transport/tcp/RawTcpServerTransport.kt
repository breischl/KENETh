package dev.breischl.keneth.transport.tcp

import dev.breischl.keneth.transport.SocketTransport
import java.io.IOException
import java.net.Socket

/**
 * A raw TCP server transport that wraps an already-accepted socket.
 *
 * Use this when accepting connections via [java.net.ServerSocket]:
 * ```kotlin
 * val accepted = serverSocket.accept()
 * val transport = RawTcpServerTransport(accepted)
 * transport.receive().collect { result ->
 *     println("Received: ${result.value}")
 * }
 * ```
 *
 * @param socket The already-connected socket from [java.net.ServerSocket.accept].
 */
class RawTcpServerTransport(socket: Socket) : SocketTransport() {
    init {
        this.socket = socket
    }

    override fun socket(): Socket {
        synchronized(lock) {
            return socket ?: throw IOException("Socket is closed")
        }
    }
}