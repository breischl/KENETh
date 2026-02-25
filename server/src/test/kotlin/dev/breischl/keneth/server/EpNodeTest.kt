package dev.breischl.keneth.server

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.values.*
import dev.breischl.keneth.transport.FrameTransport
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
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

    private class ChannelFakeFrameTransport(
        private val channel: Channel<ParseResult<Frame>> = Channel(Channel.UNLIMITED)
    ) : FrameTransport {
        val sentFrames = mutableListOf<Frame>()
        var closed = false

        override suspend fun send(frame: Frame) {
            sentFrames.add(frame)
        }

        override fun receive(): Flow<ParseResult<Frame>> = channel.consumeAsFlow()

        override fun close() {
            closed = true
            channel.close()
        }

        suspend fun enqueue(frame: ParseResult<Frame>) {
            channel.send(frame)
        }
    }

    private fun encodeMessage(message: Message): ByteArray {
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
        val connectedPeers = mutableListOf<PeerSnapshot>()
        val disconnectedPeers = mutableListOf<PeerSnapshot>()
        val parameterUpdates = mutableListOf<Pair<PeerSnapshot, Message>>()
        val errors = mutableListOf<Throwable>()

        override fun onPeerConnected(peer: PeerSnapshot) {
            events.add("connected:${peer.peerId}")
            connectedPeers.add(peer)
        }

        override fun onPeerDisconnected(peer: PeerSnapshot) {
            events.add("disconnected:${peer.peerId}")
            disconnectedPeers.add(peer)
        }

        override fun onPeerParametersUpdated(peer: PeerSnapshot, message: Message) {
            events.add("params:${peer.peerId}:${message::class.simpleName}")
            parameterUpdates.add(peer to message)
        }

        override fun onError(error: Throwable) {
            events.add("error:${error.message}")
            errors.add(error)
        }
    }

    // -- Lifecycle tests --

    @Test
    fun `start and close lifecycle completes without error`() = runTest {
        val node = EpNode(
            config = nodeConfig.copy(listenPort = 0),
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.start()
        assertNotNull(node.localPort)
        assertTrue(node.localPort!! > 0)

        node.close()
        assertNull(node.localPort)
    }

    @Test
    fun `node without listen port has no acceptor`() = runTest {
        val node = EpNode(
            config = nodeConfig,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.start() // no-op since no listenPort
        assertNull(node.localPort)

        node.close()
    }

    // -- Peer management tests --

    @Test
    fun `peer management delegates to server`() = runTest {
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())
        val node = EpNode(config = nodeConfig, server = server)

        val peerConfig = PeerConfig(peerId = "charger-1", direction = PeerDirection.INBOUND)
        node.addPeer(peerConfig)

        assertTrue(node.peers.containsKey("charger-1"))
        assertTrue(server.peers.containsKey("charger-1"))

        node.removePeer("charger-1")

        assertFalse(node.peers.containsKey("charger-1"))
        assertFalse(server.peers.containsKey("charger-1"))

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
            PeerConfig(
                peerId = "charger-1",
                direction = PeerDirection.INBOUND,
                expectedIdentity = "test-device"
            )
        )

        // Simulate inbound connection via the internal server
        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        node.server.accept(transport)

        assertContains(listener.events, "connected:charger-1")
        assertEquals(1, listener.connectedPeers.size)
        assertEquals("charger-1", listener.connectedPeers[0].peerId)

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
            PeerConfig(
                peerId = "charger-1",
                direction = PeerDirection.INBOUND,
                expectedIdentity = "test-device"
            )
        )

        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        node.server.accept(transport)

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
            PeerConfig(
                peerId = "charger-1",
                direction = PeerDirection.INBOUND,
                expectedIdentity = "test-device"
            )
        )

        val supply = SupplyParameters(voltage = Voltage(400.0))
        val demand = DemandParameters(voltage = Voltage(400.0))
        val storage = StorageParameters(soc = Percentage(75.0))

        val (fake, transport) = channelTransportWithMessages(deviceIdentity, supply, demand, storage)
        node.server.accept(transport)

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

        node.server.accept(MessageTransport(errorTransport))

        assertContains(listener.events, "error:test error")
        assertEquals(1, listener.errors.size)

        node.close()
    }

    @Test
    fun `inbound connection accepted when listening`() = runTest {
        val listener = RecordingNodeListener()
        val node = EpNode(
            config = nodeConfig.copy(listenPort = 0),
            listener = listener,
            coroutineContext = UnconfinedTestDispatcher(),
        )

        node.addPeer(
            PeerConfig(
                peerId = "charger-1",
                direction = PeerDirection.INBOUND,
                expectedIdentity = "test-device"
            )
        )
        node.start()

        // Connect a real TCP client
        val port = node.localPort!!
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
}
