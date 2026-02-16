package dev.breischl.keneth.transport.tls

import dev.breischl.keneth.transport.SocketTransport
import java.io.IOException
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * A TLS server transport that wraps an already-accepted [SSLSocket].
 *
 * Use this when accepting connections via [javax.net.ssl.SSLServerSocket]:
 * ```kotlin
 * val accepted = sslServerSocket.accept() as SSLSocket
 * val transport = TlsServerTransport(accepted, config)
 * transport.receive().collect { result ->
 *     println("Received: ${result.value}")
 * }
 * ```
 *
 * @param socket The already-connected SSL socket from [javax.net.ssl.SSLServerSocket.accept].
 * @param config The TLS configuration (used to apply [TlsConfig.clientAuth]).
 */
class TlsServerTransport(socket: SSLSocket, config: TlsConfig) : SocketTransport() {
    init {
        when (config.clientAuth) {
            ClientAuth.NONE -> { /* default */
            }

            ClientAuth.WANT -> socket.wantClientAuth = true
            ClientAuth.NEED -> socket.needClientAuth = true
        }
        socket.startHandshake()
        this.socket = socket
    }

    override fun socket(): Socket {
        synchronized(lock) {
            return socket ?: throw IOException("Socket is closed")
        }
    }
}
