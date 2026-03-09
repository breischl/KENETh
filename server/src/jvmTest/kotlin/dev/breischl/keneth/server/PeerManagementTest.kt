package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.tcp.TcpPeerConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.net.ServerSocket
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PeerManagementTest {
    private val serverIdentity = SessionParameters(identity = "test-server", type = "router")
    private val deviceIdentity = SessionParameters(identity = "device-1", type = "charger")

    private val cleanupList = mutableListOf<AutoCloseable>()

    private fun <T : AutoCloseable> T.tracked(): T {
        cleanupList.add(this)
        return this
    }

    @AfterTest
    fun tearDown() {
        cleanupList.reversed().forEach { runCatching { it.close() } }
    }

    private class RecordingListener : NodeListener {
        val events = mutableListOf<String>()
        val connectedPeers = mutableListOf<SessionSnapshot>()
        val disconnectedPeers = mutableListOf<SessionSnapshot>()

        override fun onSessionCreated(session: SessionSnapshot) {
            events.add("sessionCreated")
        }

        override fun onSessionActive(session: SessionSnapshot) {
            events.add("sessionActive")
        }

        override fun onSessionClosed(session: SessionSnapshot) {
            events.add("sessionClosed")
        }

        override fun onPeerConnected(session: SessionSnapshot) {
            events.add("peerConnected:${session.peerId}")
            synchronized(connectedPeers) { connectedPeers.add(session) }
        }

        override fun onPeerDisconnected(session: SessionSnapshot) {
            events.add("peerDisconnected:${session.peerId}")
            synchronized(disconnectedPeers) { disconnectedPeers.add(session) }
        }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5.seconds) {
            withContext(Dispatchers.Default) {
                while (!condition()) {
                    delay(10)
                }
            }
        }
    }

    // -- Unit tests --

    @Test
    fun `addPeer stores peer config`() = runTest {
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val config = PeerConfig.Inbound(peerId = "peer-1")
        node.addPeer(config)

        assertEquals(1, node.peers.size)
        assertEquals(config, node.peers["peer-1"]?.config)
        assertFalse(node.peers["peer-1"]?.isConnected ?: true)
        node.close()
    }

    @Test
    fun `removePeer removes peer`() = runTest {
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        node.addPeer(PeerConfig.Inbound(peerId = "peer-1"))
        assertEquals(1, node.peers.size)

        node.removePeer("peer-1")
        assertTrue(node.peers.isEmpty())
        node.close()
    }

    @Test
    fun `addPeer rejects duplicate peerId`() = runTest {
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        node.addPeer(PeerConfig.Inbound(peerId = "peer-1"))

        assertFailsWith<IllegalArgumentException> {
            node.addPeer(PeerConfig.Inbound(peerId = "peer-1"))
        }
        node.close()
    }

    @Test
    fun `inbound session matched to peer by identity`() = runTest {
        val listener = RecordingListener()
        val node =
            EpNode(identity = serverIdentity, nodeListener = listener, coroutineContext = UnconfinedTestDispatcher())

        node.addPeer(PeerConfig.Inbound(peerId = "peer-1", expectedIdentity = "device-1"))

        val (_, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)

        val peer = node.peers["peer-1"]!!
        assertTrue(peer.isConnected)
        assertEquals("device-1", peer.remoteIdentity)
        assertContains(listener.events, "peerConnected:peer-1")
        node.close()
    }

    @Test
    fun `inbound peer matched by peerId when expectedIdentity not set`() = runTest {
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        // peerId matches identity, no explicit expectedIdentity
        node.addPeer(PeerConfig.Inbound(peerId = "device-1"))

        val (_, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)

        val peer = node.peers["device-1"]!!
        assertTrue(peer.isConnected)
        node.close()
    }

    @Test
    fun `inbound session unmatched stays as regular session`() = runTest {
        val listener = RecordingListener()
        val node =
            EpNode(identity = serverIdentity, nodeListener = listener, coroutineContext = UnconfinedTestDispatcher())

        node.addPeer(
            PeerConfig.Inbound(
                peerId = "peer-1",
                expectedIdentity = "other-device"
            )
        )

        val (_, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)

        // Session exists
        assertEquals(1, node.sessions.size)
        // Peer not linked
        val peer = node.peers["peer-1"]!!
        assertFalse(peer.isConnected)
        assertNull(peer.remoteIdentity)
        assertFalse(listener.events.any { it.startsWith("peerConnected") })
        node.close()
    }

    @Test
    fun `peer disconnected when session closes`() = runTest {
        val listener = RecordingListener()
        val node =
            EpNode(identity = serverIdentity, nodeListener = listener, coroutineContext = UnconfinedTestDispatcher())

        node.addPeer(PeerConfig.Inbound(peerId = "device-1"))

        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)

        val peer = node.peers["device-1"]!!
        assertTrue(peer.isConnected)

        // Close the transport — session will complete and close
        fake.close()

        assertFalse(peer.isConnected)
        // Peer stays in the map
        assertTrue(node.peers.containsKey("device-1"))
        assertContains(listener.events, "peerDisconnected:device-1")
        node.close()
    }

    @Test
    fun `removePeer disconnects active session`() = runTest {
        val listener = RecordingListener()
        val node =
            EpNode(identity = serverIdentity, nodeListener = listener, coroutineContext = UnconfinedTestDispatcher())

        node.addPeer(PeerConfig.Inbound(peerId = "device-1"))

        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        val session = node.accept(transport)

        assertTrue(node.peers["device-1"]?.isConnected ?: false)

        node.removePeer("device-1")

        assertTrue(node.peers.isEmpty())
        assertEquals(SessionState.CLOSED, session.state)
        assertTrue(fake.closed)
        assertContains(listener.events, "peerDisconnected:device-1")
        node.close()
    }

    // -- Integration tests (real sockets) --

    @Test
    fun `outbound peer connects and handshakes`() = runBlocking {
        // Start a "remote" server socket that the outbound peer will connect to
        val remoteServer = ServerSocket(0).tracked()
        val port = remoteServer.localPort

        val listener = RecordingListener()
        val node = EpNode(identity = serverIdentity, nodeListener = listener).tracked()

        node.addPeer(
            PeerConfig.Outbound(
                peerId = "remote-device",
                connector = TcpPeerConnector("127.0.0.1", port),
            )
        )

        // Accept the outbound connection on the remote side
        val remoteSocket = remoteServer.accept().tracked()
        val rawRemoteTransport = dev.breischl.keneth.transport.tcp.RawTcpServerTransport(remoteSocket)
        val remoteTransport = MessageTransport(rawRemoteTransport).tracked()

        // EpNode's accept() expects the remote to send SessionParameters first
        // (same as for inbound connections). Send the device identity.
        remoteTransport.send(SessionParameters(identity = "remote-device", type = "charger"))

        // Wait for peer to become connected
        awaitCondition {
            synchronized(listener.connectedPeers) { listener.connectedPeers.isNotEmpty() }
        }

        val peer = node.peers["remote-device"]!!
        assertTrue(peer.isConnected)
        assertEquals("remote-device", peer.remoteIdentity)
    }

    @Test
    fun `peer listener callbacks fire`() = runTest {
        val listener = RecordingListener()
        val node =
            EpNode(identity = serverIdentity, nodeListener = listener, coroutineContext = UnconfinedTestDispatcher())

        node.addPeer(PeerConfig.Inbound(peerId = "device-1"))

        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)

        assertContains(listener.events, "peerConnected:device-1")

        fake.close()

        assertContains(listener.events, "peerDisconnected:device-1")
        node.close()
    }
}
