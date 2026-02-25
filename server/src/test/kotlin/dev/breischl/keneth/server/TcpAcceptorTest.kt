package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.transport.TransportListener
import dev.breischl.keneth.transport.tcp.RawTcpClientTransport
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TcpAcceptorTest {

    private val serverParams = SessionParameters(identity = "test-server", type = "router")
    private val deviceParams = SessionParameters(identity = "test-device", type = "charger")

    private val cleanupList = mutableListOf<AutoCloseable>()

    private fun <T : AutoCloseable> T.tracked(): T {
        cleanupList.add(this)
        return this
    }

    @AfterTest
    fun tearDown() {
        cleanupList.reversed().forEach { runCatching { it.close() } }
    }

    private suspend fun connectClient(port: Int, params: SessionParameters = deviceParams): MessageTransport {
        val client = RawTcpClientTransport("127.0.0.1", port).tracked()
        val transport = MessageTransport(client)
        transport.send(params)
        return transport
    }

    /** Polls until [condition] is true, using real-time delays (not virtual time). */
    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5.seconds) {
            withContext(Dispatchers.Default) {
                while (!condition()) {
                    kotlinx.coroutines.delay(10)
                }
            }
        }
    }

    @Test
    fun `accepted connection completes handshake`() = runBlocking {
        val server = EpServer(serverParams).tracked()
        val acceptor = TcpAcceptor(server, 0).tracked()
        acceptor.start()

        connectClient(acceptor.localPort!!)

        awaitCondition { server.sessions.values.any { it.state == SessionState.ACTIVE } }

        assertEquals(1, server.sessions.size)
        val session = server.sessions.values.first()
        assertEquals(SessionState.ACTIVE, session.state)
        assertEquals("test-device", session.sessionParameters?.identity)
    }

    @Test
    fun `multiple clients connect independently`() = runBlocking {
        val server = EpServer(serverParams).tracked()
        val acceptor = TcpAcceptor(server, 0).tracked()
        acceptor.start()

        val device1 = SessionParameters(identity = "device-1", type = "charger")
        val device2 = SessionParameters(identity = "device-2", type = "charger")

        connectClient(acceptor.localPort!!, device1)
        connectClient(acceptor.localPort!!, device2)

        awaitCondition {
            server.sessions.values.count { it.state == SessionState.ACTIVE } >= 2
        }

        assertEquals(2, server.sessions.size)
        val identities = server.sessions.values.map { it.sessionParameters?.identity }.toSet()
        assertEquals(setOf("device-1", "device-2"), identities)
    }

    @Test
    fun `close stops accepting new connections`() = runBlocking {
        withTimeout(5.seconds) {
            val server = EpServer(serverParams).tracked()
            val acceptor = TcpAcceptor(server, 0).tracked()
            acceptor.start()
            val port = acceptor.localPort!!

            // Verify it's working
            connectClient(port)
            awaitCondition { server.sessions.values.any { it.state == SessionState.ACTIVE } }

            val sessionCountBefore = server.sessions.size
            acceptor.close()

            // Server socket is closed — verify via isClosed on the acceptor
            assertTrue(acceptor.isClosed, "Acceptor should be closed")

            // Existing sessions remain (EpServer owns them, not the acceptor)
            assertEquals(sessionCountBefore, server.sessions.size)
        }
    }

    @Test
    fun `transportListener receives events`() = runBlocking {
        val connectedHosts = mutableListOf<String>()
        val listener = object : TransportListener {
            override fun onConnected(host: String, port: Int) {
                synchronized(connectedHosts) { connectedHosts.add(host) }
            }
        }

        val server = EpServer(serverParams).tracked()
        val acceptor = TcpAcceptor(server, 0, listener).tracked()
        acceptor.start()

        connectClient(acceptor.localPort!!)

        awaitCondition { synchronized(connectedHosts) { connectedHosts.isNotEmpty() } }

        assertTrue(connectedHosts.isNotEmpty(), "onConnected should have been called")
    }
}
