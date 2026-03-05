package dev.breischl.keneth.transport.tls

import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.transport.TransportTracker
import dev.breischl.keneth.transport.assertFrameEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TlsTransportTest {
    private val password = "changeit".toCharArray()
    private val tracker = TransportTracker()

    @AfterTest
    fun tearDown() {
        tracker.tearDown()
    }

    private fun loadKeyStore(resource: String) =
        TlsConfig.loadKeyStore(javaClass.classLoader.getResourceAsStream(resource)!!, password)

    private fun startTlsServer(
        serverConfig: TlsConfig = TlsConfig(
            keyStore = loadKeyStore("server.p12"),
            keyStorePassword = password
        )
    ): SSLServerSocket {
        val sslContext = serverConfig.createSslContext()
        val server = sslContext.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        tracker.track(server)
        return server
    }

    private suspend fun acceptTlsConnection(
        server: SSLServerSocket,
        config: TlsConfig = TlsConfig()
    ): TlsServerTransport = withContext(Dispatchers.IO) {
        val socket = server.accept() as SSLSocket
        val transport = TlsServerTransport(socket, config)
        tracker.serverTransport = transport
        transport
    }

    private fun createTlsClient(server: SSLServerSocket, config: TlsConfig? = null): TlsClientTransport {
        val clientConfig = config ?: TlsConfig(insecureTrustAll = true)
        val client = TlsClientTransport("localhost", server.localPort, clientConfig)
        tracker.clientTransport = client
        return client
    }

    // -- Tests --

    @Test
    fun `send and receive with custom trust store`() = runTest {
        val server = startTlsServer()
        val clientConfig = TlsConfig(
            trustStore = loadKeyStore("truststore.p12"),
            verifyHostname = false
        )
        val client = createTlsClient(server, clientConfig)

        val frame = Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x01, 0x02, 0x03))

        val sendJob = launch { client.send(frame) }
        val serverSide = acceptTlsConnection(server)
        sendJob.join()

        // Close client so server sees EOF
        client.close()

        val result = serverSide.receive().first()
        assertFrameEquals(frame, result)
    }

    @Test
    fun `insecure mode trusts any certificate`() = runTest {
        val server = startTlsServer()
        val client = createTlsClient(server)

        val frame = Frame(emptyMap(), 0x0000_0042u, byteArrayOf())

        val sendJob = launch { client.send(frame) }
        val serverSide = acceptTlsConnection(server)
        sendJob.join()

        client.close()

        val result = serverSide.receive().first()
        assertFrameEquals(frame, result)
    }

    @Test
    fun `bidirectional exchange over TLS`() = runTest {
        val server = startTlsServer()
        val clientConfig = TlsConfig(
            trustStore = loadKeyStore("truststore.p12"),
            verifyHostname = false
        )
        val client = createTlsClient(server, clientConfig)

        val clientFrame = Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x01))
        val serverFrame = Frame(emptyMap(), 0x0000_0002u, byteArrayOf(0x02))

        // Client sends first to establish the connection
        val sendJob = launch { client.send(clientFrame) }
        val serverSide = acceptTlsConnection(server)
        sendJob.join()

        // Server also sends
        serverSide.send(serverFrame)

        val serverResult = CompletableDeferred<ParseResult<Frame>>()
        val clientResult = CompletableDeferred<ParseResult<Frame>>()

        val serverJob = launch(Dispatchers.IO) {
            serverSide.receive().collect { serverResult.complete(it) }
        }
        val clientJob = launch(Dispatchers.IO) {
            client.receive().collect { clientResult.complete(it) }
        }

        val receivedByServer = serverResult.await()
        val receivedByClient = clientResult.await()

        // Close transports so the blocked producers see EOF and exit
        client.close()
        serverSide.close()
        serverJob.join()
        clientJob.join()

        assertFrameEquals(clientFrame, receivedByServer)
        assertFrameEquals(serverFrame, receivedByClient)
    }

    @Test
    fun `close completes receive flow`() = runTest {
        val server = startTlsServer()
        val client = createTlsClient(server)

        val receiveDeferred = async { client.receive().toList() }
        val accepted = withContext(Dispatchers.IO) {
            val socket = server.accept() as SSLSocket
            socket.startHandshake()
            socket
        }

        withContext(Dispatchers.IO) {
            accepted.close()
        }

        val results = receiveDeferred.await()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `send after close throws`() = runTest {
        val server = startTlsServer()
        val client = createTlsClient(server)

        val frame = Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x01))

        // Connect by sending a frame
        val sendJob = launch { client.send(frame) }
        acceptTlsConnection(server)
        sendJob.join()

        // Close everything
        server.close()
        client.close()

        assertFailsWith<Exception> {
            client.send(frame)
        }
    }

    @Test
    fun `multiple frames in sequence`() = runTest {
        val server = startTlsServer()
        val client = createTlsClient(server)

        val frames = listOf(
            Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x01)),
            Frame(emptyMap(), 0x0000_0002u, byteArrayOf(0x02)),
            Frame(emptyMap(), 0x0000_0003u, byteArrayOf(0x03)),
        )

        val sendJob = launch {
            for (frame in frames) {
                client.send(frame)
            }
        }
        val serverSide = acceptTlsConnection(server)
        sendJob.join()

        // Close client so server sees EOF
        client.close()

        val received = serverSide.receive().take(frames.size).toList()

        assertEquals(frames.size, received.size)
        for (i in frames.indices) {
            assertFrameEquals(frames[i], received[i])
        }
    }

    @Test
    fun `server sends to client`() = runTest {
        val server = startTlsServer()
        val client = createTlsClient(server)

        val frame = Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x01, 0x02, 0x03))

        // Client must initiate the TLS connection by sending first
        val initFrame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf())
        val sendJob = launch { client.send(initFrame) }
        val serverSide = acceptTlsConnection(server)
        sendJob.join()

        // Server sends
        serverSide.send(frame)
        // Close server so client sees EOF
        serverSide.close()

        val result = client.receive().first()
        assertFrameEquals(frame, result)
    }

    @Test
    fun `local close completes receive flow`() = runTest {
        val server = startTlsServer()
        val client = createTlsClient(server)

        // Client must initiate TLS connection
        val initFrame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf())
        val sendJob = launch { client.send(initFrame) }
        acceptTlsConnection(server)
        sendJob.join()

        // Start receiving on IO dispatcher — will block on read
        val receiveDeferred = async(Dispatchers.IO) { client.receive().toList() }

        // Real delay to let the receive coroutine start blocking on IO
        withContext(Dispatchers.IO) { Thread.sleep(100) }

        // Close the local transport while receive is blocked
        client.close()

        val results = receiveDeferred.await()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `client close is idempotent`() = runTest {
        val server = startTlsServer()
        val client = createTlsClient(server)

        val frame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf())
        val sendJob = launch { client.send(frame) }
        acceptTlsConnection(server)
        sendJob.join()

        client.close()
        client.close()
        client.close()
    }

    @Test
    fun `server transport close is idempotent`() = runTest {
        val server = startTlsServer()
        val client = createTlsClient(server)

        val frame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf())
        val sendJob = launch { client.send(frame) }
        val serverSide = acceptTlsConnection(server)
        sendJob.join()

        serverSide.close()
        serverSide.close()
        serverSide.close()
    }

    @Test
    fun `mTLS with client certificate`() = runTest {
        val serverConfig = TlsConfig(
            keyStore = loadKeyStore("server.p12"),
            keyStorePassword = password,
            trustStore = loadKeyStore("server-truststore.p12"),
            clientAuth = ClientAuth.NEED
        )
        val server = startTlsServer(serverConfig)

        val clientConfig = TlsConfig(
            keyStore = loadKeyStore("client.p12"),
            keyStorePassword = password,
            trustStore = loadKeyStore("truststore.p12"),
            verifyHostname = false
        )
        val client = createTlsClient(server, clientConfig)

        val frame = Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x0A, 0x0B))

        val sendJob = launch { client.send(frame) }
        val serverSide = acceptTlsConnection(server, serverConfig)
        sendJob.join()

        client.close()

        val result = serverSide.receive().first()
        assertFrameEquals(frame, result)
    }
}
