package dev.breischl.keneth.server

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.SupplyParameters
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.values.Current
import dev.breischl.keneth.core.values.Voltage
import dev.breischl.keneth.transport.FrameTransport
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import java.net.ServerSocket
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PeerManagementTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

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

    // -- Fake transport infrastructure (same pattern as EpServerTest) --

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

    private class RecordingListener : ServerListener {
        val events = mutableListOf<String>()
        val connectedPeers = mutableListOf<Peer>()
        val disconnectedPeers = mutableListOf<Peer>()

        override fun onSessionCreated(session: DeviceSession) {
            events.add("sessionCreated")
        }

        override fun onSessionActive(session: DeviceSession) {
            events.add("sessionActive")
        }

        override fun onSessionClosed(session: DeviceSession) {
            events.add("sessionClosed")
        }

        override fun onPeerConnected(peer: Peer) {
            events.add("peerConnected:${peer.peerId}")
            synchronized(connectedPeers) { connectedPeers.add(peer) }
        }

        override fun onPeerDisconnected(peer: Peer) {
            events.add("peerDisconnected:${peer.peerId}")
            synchronized(disconnectedPeers) { disconnectedPeers.add(peer) }
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
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val config = PeerConfig(peerId = "peer-1", direction = PeerDirection.INBOUND)
        server.addPeer(config)

        assertEquals(1, server.peers.size)
        assertEquals(config, server.peers["peer-1"]?.config)
        assertEquals(ConnectionState.DISCONNECTED, server.peers["peer-1"]?.connectionState)
        server.close()
    }

    @Test
    fun `removePeer removes peer`() = runTest {
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        server.addPeer(PeerConfig(peerId = "peer-1", direction = PeerDirection.INBOUND))
        assertEquals(1, server.peers.size)

        server.removePeer("peer-1")
        assertTrue(server.peers.isEmpty())
        server.close()
    }

    @Test
    fun `addPeer rejects duplicate peerId`() = runTest {
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        server.addPeer(PeerConfig(peerId = "peer-1", direction = PeerDirection.INBOUND))

        assertFailsWith<IllegalArgumentException> {
            server.addPeer(PeerConfig(peerId = "peer-1", direction = PeerDirection.INBOUND))
        }
        server.close()
    }

    @Test
    fun `addPeer rejects outbound without host`() = runTest {
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        assertFailsWith<IllegalArgumentException> {
            server.addPeer(PeerConfig(peerId = "peer-1", direction = PeerDirection.OUTBOUND))
        }
        server.close()
    }

    @Test
    fun `inbound session matched to peer by identity`() = runTest {
        val listener = RecordingListener()
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        server.addPeer(PeerConfig(peerId = "peer-1", direction = PeerDirection.INBOUND, expectedIdentity = "device-1"))

        val (_, transport) = channelTransportWithMessages(deviceIdentity)
        server.accept(transport)

        val peer = server.peers["peer-1"]!!
        assertEquals(ConnectionState.CONNECTED, peer.connectionState)
        assertEquals("device-1", peer.remoteIdentity)
        assertContains(listener.events, "peerConnected:peer-1")
        server.close()
    }

    @Test
    fun `inbound peer matched by peerId when expectedIdentity not set`() = runTest {
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        // peerId matches identity, no explicit expectedIdentity
        server.addPeer(PeerConfig(peerId = "device-1", direction = PeerDirection.INBOUND))

        val (_, transport) = channelTransportWithMessages(deviceIdentity)
        server.accept(transport)

        val peer = server.peers["device-1"]!!
        assertEquals(ConnectionState.CONNECTED, peer.connectionState)
        server.close()
    }

    @Test
    fun `inbound session unmatched stays as regular session`() = runTest {
        val listener = RecordingListener()
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        server.addPeer(
            PeerConfig(
                peerId = "peer-1",
                direction = PeerDirection.INBOUND,
                expectedIdentity = "other-device"
            )
        )

        val (_, transport) = channelTransportWithMessages(deviceIdentity)
        server.accept(transport)

        // Session exists
        assertEquals(1, server.sessions.size)
        // Peer not linked
        val peer = server.peers["peer-1"]!!
        assertEquals(ConnectionState.DISCONNECTED, peer.connectionState)
        assertNull(peer.remoteIdentity)
        assertFalse(listener.events.any { it.startsWith("peerConnected") })
        server.close()
    }

    @Test
    fun `peer tracks remote parameters`() = runTest {
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        server.addPeer(PeerConfig(peerId = "device-1", direction = PeerDirection.INBOUND))

        val supply = SupplyParameters(voltage = Voltage(400.0), current = Current(32.0))
        val (_, transport) = channelTransportWithMessages(deviceIdentity, supply)
        server.accept(transport)

        val peer = server.peers["device-1"]!!
        assertNotNull(peer.latestSupply)
        assertEquals(400.0, peer.latestSupply!!.voltage?.volts)
        assertEquals(32.0, peer.latestSupply!!.current?.amperes)
        server.close()
    }

    @Test
    fun `peer disconnected when session closes`() = runTest {
        val listener = RecordingListener()
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        server.addPeer(PeerConfig(peerId = "device-1", direction = PeerDirection.INBOUND))

        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        server.accept(transport)

        val peer = server.peers["device-1"]!!
        assertEquals(ConnectionState.CONNECTED, peer.connectionState)

        // Close the transport — session will complete and close
        fake.close()

        assertEquals(ConnectionState.DISCONNECTED, peer.connectionState)
        // Peer stays in the map
        assertTrue(server.peers.containsKey("device-1"))
        assertContains(listener.events, "peerDisconnected:device-1")
        server.close()
    }

    @Test
    fun `removePeer disconnects active session`() = runTest {
        val listener = RecordingListener()
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        server.addPeer(PeerConfig(peerId = "device-1", direction = PeerDirection.INBOUND))

        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        val session = server.accept(transport)

        assertEquals(ConnectionState.CONNECTED, server.peers["device-1"]?.connectionState)

        server.removePeer("device-1")

        assertTrue(server.peers.isEmpty())
        assertEquals(SessionState.CLOSED, session.state)
        assertTrue(fake.closed)
        assertContains(listener.events, "peerDisconnected:device-1")
        server.close()
    }

    // -- Integration tests (real sockets) --

    @Test
    fun `outbound peer connects and handshakes`() = runBlocking {
        // Start a "remote" server socket that the outbound peer will connect to
        val remoteServer = ServerSocket(0).tracked()
        val port = remoteServer.localPort

        val listener = RecordingListener()
        val server = EpServer(serverIdentity, listener).tracked()

        server.addPeer(
            PeerConfig(
                peerId = "remote-device",
                host = "127.0.0.1",
                port = port,
                direction = PeerDirection.OUTBOUND,
            )
        )

        // Accept the outbound connection on the remote side
        val remoteSocket = remoteServer.accept().tracked()
        val rawRemoteTransport = dev.breischl.keneth.transport.tcp.RawTcpServerTransport(remoteSocket)
        val remoteTransport = MessageTransport(rawRemoteTransport).tracked()

        // EpServer's accept() expects the remote to send SessionParameters first
        // (same as for inbound connections). Send the device identity.
        remoteTransport.send(SessionParameters(identity = "remote-device", type = "charger"))

        // Wait for peer to become connected
        awaitCondition {
            synchronized(listener.connectedPeers) { listener.connectedPeers.isNotEmpty() }
        }

        val peer = server.peers["remote-device"]!!
        assertEquals(ConnectionState.CONNECTED, peer.connectionState)
        assertEquals("remote-device", peer.remoteIdentity)
    }

    @Test
    fun `peer listener callbacks fire`() = runTest {
        val listener = RecordingListener()
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        server.addPeer(PeerConfig(peerId = "device-1", direction = PeerDirection.INBOUND))

        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        server.accept(transport)

        assertContains(listener.events, "peerConnected:device-1")

        fake.close()

        assertContains(listener.events, "peerDisconnected:device-1")
        server.close()
    }
}
