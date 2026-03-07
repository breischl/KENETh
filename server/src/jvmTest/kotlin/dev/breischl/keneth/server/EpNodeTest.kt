package dev.breischl.keneth.server

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.values.*
import dev.breischl.keneth.transport.FrameTransport
import dev.breischl.keneth.transport.InMemoryPeerConnector
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EpNodeTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    private val serverIdentity = SessionParameters(identity = "test-node", type = "router")
    private val deviceIdentity = SessionParameters(identity = "test-device", type = "charger")

    private val nodeConfig = NodeConfig(identity = serverIdentity)

    private fun encodeMessage(message: Message): ByteArray {
        // payloadSerializer is declared as KSerializer<out Message> (covariant) to allow subtype
        // serializers. The cast to KSerializer<Message> is safe here because the serializer and
        // the value come from the same concrete object — the serializer only receives values of
        // exactly that type, so the invariant requirement is never violated at runtime.
        @Suppress("UNCHECKED_CAST")
        return cbor.encodeToByteArray(
            message.payloadSerializer as KSerializer<Message>,
            message
        )
    }

    private fun frameResultFor(message: Message): ParseResult<Frame> {
        val payload = encodeMessage(message)
        return ParseResult.success(
            Frame(emptyMap(), message.typeId, payload),
            emptyList()
        )
    }

    private suspend fun channelTransportWithMessages(vararg messages: Message): Pair<ChannelFakeFrameTransport, MessageTransport> {
        val fake = ChannelFakeFrameTransport()
        for (msg in messages) {
            fake.enqueue(frameResultFor(msg))
        }
        return fake to MessageTransport(fake)
    }

    /** Records all NodeListener calls for verification. */
    private class RecordingNodeListener : NodeListener {
        val events = mutableListOf<String>()
        val connectedPeers = mutableListOf<SessionSnapshot>()
        val disconnectedPeers = mutableListOf<SessionSnapshot>()
        val parameterUpdates = mutableListOf<Pair<SessionSnapshot, Message>>()
        val errors = mutableListOf<Throwable>()

        override fun onPeerConnected(session: SessionSnapshot) {
            events.add("connected:${session.peerId}")
            connectedPeers.add(session)
        }

        override fun onPeerDisconnected(session: SessionSnapshot) {
            events.add("disconnected:${session.peerId}")
            disconnectedPeers.add(session)
        }

        override fun onPeerParametersUpdated(session: SessionSnapshot, message: Message) {
            events.add("params:${session.peerId}:${message::class.simpleName}")
            parameterUpdates.add(session to message)
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
            config = nodeConfig.copy(acceptor = acceptor),
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
            config = nodeConfig,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.start() // no-op since no acceptor
        node.close()
    }

    // -- Peer management tests --

    @Test
    fun `peer management add and remove`() = runTest {
        val node = EpNode(config = nodeConfig, coroutineContext = UnconfinedTestDispatcher())

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
            config = nodeConfig,
            listener = listener,
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
            config = nodeConfig,
            listener = listener,
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
    fun `NodeListener onPeerParametersUpdated fires for Supply Demand Storage`() = runTest {
        val listener = RecordingNodeListener()
        val node = EpNode(
            config = nodeConfig,
            listener = listener,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.addPeer(
            PeerConfig.Inbound(
                peerId = "charger-1",
                expectedIdentity = "test-device"
            )
        )

        val supply = SupplyParameters(voltage = Voltage(400.0))
        val demand = DemandParameters(voltage = Voltage(400.0))
        val storage = StorageParameters(soc = Percentage(75.0))

        val (fake, transport) = channelTransportWithMessages(deviceIdentity, supply, demand, storage)
        node.accept(transport)

        assertContains(listener.events, "params:charger-1:SupplyParameters")
        assertContains(listener.events, "params:charger-1:DemandParameters")
        assertContains(listener.events, "params:charger-1:StorageParameters")
        assertEquals(3, listener.parameterUpdates.size)

        fake.close()
        node.close()
    }

    @Test
    fun `NodeListener onError fires on session error`() = runTest {
        val listener = RecordingNodeListener()
        val node = EpNode(
            config = nodeConfig,
            listener = listener,
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
            config = nodeConfig.copy(acceptor = acceptor),
            listener = listener,
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
            config = nodeConfig,
            listener = object : NodeListener {
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
            config = nodeConfig,
            listener = object : NodeListener {
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
            config = nodeConfig,
            listener = object : NodeListener {
                override fun onSessionError(session: SessionSnapshot, error: Throwable) {
                    // just needs to not throw
                }
            },
            coroutineContext = UnconfinedTestDispatcher(),
        )
        val errorTransport = object : dev.breischl.keneth.transport.FrameTransport {
            override suspend fun send(frame: dev.breischl.keneth.core.frames.Frame) {}
            override fun receive(): kotlinx.coroutines.flow.Flow<dev.breischl.keneth.core.parsing.ParseResult<dev.breischl.keneth.core.frames.Frame>> =
                kotlinx.coroutines.flow.flow { throw RuntimeException("test error") }
            override fun close() {}
        }
        node.accept(MessageTransport(errorTransport))
        node.close()
    }

    // -- SessionSnapshot tests --

    @Test
    fun `SessionSnapshot includes peerId when session is linked to a peer`() = runTest {
        val node = EpNode(config = nodeConfig, coroutineContext = UnconfinedTestDispatcher())
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
            config = NodeConfig(identity = nodeIdentity),
            listener = listener,
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
