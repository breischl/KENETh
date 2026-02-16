package dev.breischl.keneth.transport.tls

import dev.breischl.keneth.transport.SocketTransport
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * A TLS client transport that creates outbound TLS connections.
 *
 * Example usage:
 * ```kotlin
 * val config = TlsConfig(trustStore = myTrustStore)
 * val transport = TlsClientTransport("charger.example.com", 56540, config)
 * try {
 *     transport.send(pingFrame)
 * } finally {
 *     transport.close()
 * }
 * ```
 *
 * @property host The hostname or IP address of the remote endpoint.
 * @property port The port number to connect to.
 * @property config The TLS configuration for the connection.
 */
class TlsClientTransport(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val config: TlsConfig
) : SocketTransport() {

    override fun socket(): Socket {
        synchronized(lock) {
            socket?.let { if (!it.isClosed) return it }

            val sslContext = config.createSslContext()
            val newSocket = sslContext.socketFactory.createSocket(host, port) as SSLSocket

            if (!config.insecureTrustAll && config.verifyHostname) {
                val sslParams = newSocket.sslParameters
                sslParams.endpointIdentificationAlgorithm = "HTTPS"
                newSocket.sslParameters = sslParams
            }

            newSocket.startHandshake()
            socket = newSocket
            return newSocket
        }
    }
}
