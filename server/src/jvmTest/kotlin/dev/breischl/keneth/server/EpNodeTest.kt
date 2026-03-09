package dev.breischl.keneth.server

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.transport.FrameTransport
import dev.breischl.keneth.transport.InMemoryPeerConnector
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EpNodeTest {
    private val serverIdentity = SessionParameters(identity = "test-node", type = "router")
    private val deviceIdentity = SessionParameters(identity = "test-device", type = "charger")

    /** Records all NodeListener calls for verification. */
    private class RecordingNodeListener : NodeListener {
        val events = mutableListOf<String>()
        val connectedPeers = mutableListOf<SessionSnapshot>()
        val disconnectedPeers = mutableListOf<SessionSnapshot>()
        val errors = mutableListOf<Throwable>()

        override fun onPeerConnected(session: SessionSnapshot) {
            events.add("connected:${session.peerId}")
            connectedPeers.add(session)
        }

        override fun onPeerDisconnected(session: SessionSnapshot) {
            events.add("disconnected:${session.peerId}")
            disconnectedPeers.add(session)
        }

        override fun onSessionError(session: SessionSnapshot, error: Throwable) {
            events.add("error:${error.message}")
            errors.add(error)
        }
    }

    // -- Lifecycle tests --

    @Test
    fun `start and close lifecycle completes without error`() = runTest {
        val acceptor = TcpAcceptor(0)
        val node = EpNode(
            identity = serverIdentity,
            acceptor = acceptor,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.start()
        assertNotNull(acceptor.localPort)
        assertTrue(acceptor.localPort!! > 0)

        node.close()
    }

    @Test
    fun `node without acceptor starts with no-op`() = runTest {
        val node = EpNode(
            identity = serverIdentity,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.start() // no-op since no acceptor
        node.close()
    }

    // -- Peer management tests --

    @Test
    fun `peer management add and remove`() = runTest {
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val peerConfig = PeerConfig.Inbound(peerId = "charger-1")
        node.addPeer(peerConfig)

        assertTrue(node.peers.containsKey("charger-1"))

        node.removePeer("charger-1")

        assertFalse(node.peers.containsKey("charger-1"))

        node.close()
    }

    // -- NodeListener callback tests --

    @Test
    fun `NodeListener onPeerConnected fires when inbound peer connects`() = runTest {
        val listener = RecordingNodeListener()
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = listener,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.addPeer(
            PeerConfig.Inbound(
                peerId = "charger-1",
                expectedIdentity = "test-device"
            )
        )

        // Simulate inbound connection via the internal server
        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)

        assertContains(listener.events, "connected:charger-1")
        assertEquals(1, listener.connectedPeers.size)
        assertEquals("charger-1", listener.connectedPeers[0].peerId!!)

        fake.close()
        node.close()
    }

    @Test
    fun `NodeListener onPeerDisconnected fires when peer session closes`() = runTest {
        val listener = RecordingNodeListener()
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = listener,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.addPeer(
            PeerConfig.Inbound(
                peerId = "charger-1",
                expectedIdentity = "test-device"
            )
        )

        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)

        // Close the transport to trigger disconnect
        fake.close()

        assertContains(listener.events, "connected:charger-1")
        assertContains(listener.events, "disconnected:charger-1")

        node.close()
    }

    @Test
    fun `NodeListener onError fires on session error`() = runTest {
        val listener = RecordingNodeListener()
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = listener,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        // Create a transport that throws during receive
        val errorTransport = object : FrameTransport {
            override suspend fun send(frame: Frame) {}
            override fun receive(): Flow<ParseResult<Frame>> = kotlinx.coroutines.flow.flow {
                throw RuntimeException("test error")
            }

            override fun close() {}
        }

        node.accept(MessageTransport(errorTransport))

        assertContains(listener.events, "error:test error")
        assertEquals(1, listener.errors.size)

        node.close()
    }

    @Test
    fun `inbound connection accepted when listening`() = runTest {
        val listener = RecordingNodeListener()
        val acceptor = TcpAcceptor(0)
        val node = EpNode(
            identity = serverIdentity,
            acceptor = acceptor,
            nodeListener = listener,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.addPeer(
            PeerConfig.Inbound(
                peerId = "charger-1",
                expectedIdentity = "test-device"
            )
        )
        node.start()

        // Connect a real TCP client
        val port = acceptor.localPort!!
        val clientTransport = dev.breischl.keneth.transport.tcp.RawTcpClientTransport("127.0.0.1", port)
        val clientMsgTransport = MessageTransport(clientTransport)

        // Send handshake
        clientMsgTransport.send(deviceIdentity)

        // Poll until the peer is connected (real TCP needs real time)
        val deadline = System.currentTimeMillis() + 5_000
        while (!listener.events.contains("connected:charger-1")) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for peer connection. Events: ${listener.events}")
            }
            kotlinx.coroutines.delay(50)
        }

        assertContains(listener.events, "connected:charger-1")

        clientMsgTransport.close()
        node.close()
    }

    // -- Session-level listener tests --

    @Test
    fun `NodeListener onSessionCreated fires on accept`() = runTest {
        val events = mutableListOf<String>()
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = object : NodeListener {
                override fun onSessionCreated(session: SessionSnapshot) { events.add("created") }
            },
            coroutineContext = UnconfinedTestDispatcher(),
        )
        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)
        assertContains(events, "created")
        fake.close()
        node.close()
    }

    @Test
    fun `NodeListener onSessionActive fires after handshake`() = runTest {
        val events = mutableListOf<String>()
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = object : NodeListener {
                override fun onSessionActive(session: SessionSnapshot) { events.add("active") }
            },
            coroutineContext = UnconfinedTestDispatcher(),
        )
        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        node.accept(transport)
        assertContains(events, "active")
        fake.close()
        node.close()
    }

    @Test
    fun `NodeListener onSessionError fires on transport error`() = runTest {
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = object : NodeListener {
                override fun onSessionError(session: SessionSnapshot, error: Throwable) {
                    // just needs to not throw
                }
            },
            coroutineContext = UnconfinedTestDispatcher(),
        )
        val errorTransport = object : FrameTransport {
            override suspend fun send(frame: Frame) {}
            override fun receive(): Flow<ParseResult<Frame>> =
                kotlinx.coroutines.flow.flow { throw RuntimeException("test error") }
            override fun close() {}
        }
        node.accept(MessageTransport(errorTransport))
        node.close()
    }

    // -- SessionSnapshot tests --

    @Test
    fun `SessionSnapshot includes peerId when session is linked to a peer`() = runTest {
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())
        node.addPeer(PeerConfig.Inbound(peerId = "charger-1", expectedIdentity = "test-device"))
        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        val session = node.accept(transport)
        val peer = node.peerForSession(session.id)
        val snap = session.snapshot(peerId = peer?.peerId)
        assertEquals("charger-1", snap.peerId)
        assertEquals("test-device", snap.remoteIdentity)
        assertNotNull(snap.sessionParameters)
        fake.close()
        node.close()
    }

    // -- InMemoryFrameTransport integration --

    @Test
    fun `outbound peer connects via InMemoryPeerConnector without network`() = runTest {
        // Demonstrates that InMemoryFrameTransport can replace TCP for browser-based simulation.
        // One node uses InMemoryPeerConnector as its outbound strategy; the remote side is
        // driven manually via the paired transport (simulating a device or another node).
        val nodeIdentity = SessionParameters(identity = "sim-node", type = "router")
        val peerIdentity = SessionParameters(identity = "sim-device", type = "charger")

        val listener = RecordingNodeListener()
        val node = EpNode(
            identity = nodeIdentity,
            nodeListener = listener,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        val connector = InMemoryPeerConnector()

        node.addPeer(
            PeerConfig.Outbound(
                peerId = "sim-device",
                connector = connector,
                expectedIdentity = "sim-device",
            )
        )

        // Drive the remote side: send SessionParameters to trigger the handshake
        MessageTransport(connector.remoteTransport).send(peerIdentity)

        assertContains(listener.events, "connected:sim-device")
        assertEquals("sim-device", node.peers["sim-device"]?.remoteIdentity)

        node.close()
    }
}
