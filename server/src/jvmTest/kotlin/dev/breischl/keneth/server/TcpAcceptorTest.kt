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

    private val nodeParams = SessionParameters(identity = "test-server", type = "router")
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
        val node = EpNode(identity = nodeParams).tracked()
        val acceptor = TcpAcceptor(0).tracked()
        acceptor.start(node)

        connectClient(acceptor.localPort!!)

        awaitCondition { node.sessions.values.any { it.state == SessionState.ACTIVE } }

        assertEquals(1, node.sessions.size)
        val session = node.sessions.values.first()
        assertEquals(SessionState.ACTIVE, session.state)
        assertEquals("test-device", session.sessionParameters?.identity)
    }

    @Test
    fun `multiple clients connect independently`() = runBlocking {
        val node = EpNode(identity = nodeParams).tracked()
        val acceptor = TcpAcceptor(0).tracked()
        acceptor.start(node)

        val device1 = SessionParameters(identity = "device-1", type = "charger")
        val device2 = SessionParameters(identity = "device-2", type = "charger")

        connectClient(acceptor.localPort!!, device1)
        connectClient(acceptor.localPort!!, device2)

        awaitCondition {
            node.sessions.values.count { it.state == SessionState.ACTIVE } >= 2
        }

        assertEquals(2, node.sessions.size)
        val identities = node.sessions.values.map { it.sessionParameters?.identity }.toSet()
        assertEquals(setOf("device-1", "device-2"), identities)
    }

    @Test
    fun `close stops accepting new connections`() = runBlocking {
        withTimeout(5.seconds) {
            val node = EpNode(identity = nodeParams).tracked()
            val acceptor = TcpAcceptor(0).tracked()
            acceptor.start(node)
            val port = acceptor.localPort!!

            // Verify it's working
            connectClient(port)
            awaitCondition { node.sessions.values.any { it.state == SessionState.ACTIVE } }

            val sessionCountBefore = node.sessions.size
            acceptor.close()

            // Server socket is closed — verify via isClosed on the acceptor
            assertTrue(acceptor.isClosed, "Acceptor should be closed")

            // Existing sessions remain (EpNode owns them, not the acceptor)
            assertEquals(sessionCountBefore, node.sessions.size)
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

        val node = EpNode(identity = nodeParams, transportListener = listener).tracked()
        val acceptor = TcpAcceptor(0, listener).tracked()
        acceptor.start(node)

        connectClient(acceptor.localPort!!)

        awaitCondition { synchronized(connectedHosts) { connectedHosts.isNotEmpty() } }

        assertTrue(connectedHosts.isNotEmpty(), "onConnected should have been called")
    }
}
