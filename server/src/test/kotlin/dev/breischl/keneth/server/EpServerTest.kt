package dev.breischl.keneth.server

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.values.*
import dev.breischl.keneth.transport.FrameTransport
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EpServerTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    private val serverIdentity = SessionParameters(identity = "test-server", type = "router")
    private val deviceIdentity = SessionParameters(identity = "test-device", type = "charger")

    private class FakeFrameTransport(
        private val incomingFrames: List<ParseResult<Frame>> = emptyList()
    ) : FrameTransport {
        val sentFrames = mutableListOf<Frame>()
        var closed = false

        override suspend fun send(frame: Frame) {
            sentFrames.add(frame)
        }

        override fun receive(): Flow<ParseResult<Frame>> = incomingFrames.asFlow()

        override fun close() {
            closed = true
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

    private fun transportWithMessages(vararg messages: Message): Pair<FakeFrameTransport, MessageTransport> {
        val frames = messages.map { frameResultFor(it) }
        val fake = FakeFrameTransport(frames)
        return fake to MessageTransport(fake)
    }

    /**
     * A fake transport backed by a [Channel], so the receive flow stays open
     * until the channel is explicitly closed. Useful for tests that need the
     * session to remain ACTIVE while they call server APIs.
     */
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

    private suspend fun channelTransportWithMessages(vararg messages: Message): Pair<ChannelFakeFrameTransport, MessageTransport> {
        val fake = ChannelFakeFrameTransport()
        for (msg in messages) {
            fake.enqueue(frameResultFor(msg))
        }
        return fake to MessageTransport(fake)
    }

    private fun decodeSentSessionParameters(fake: FakeFrameTransport, index: Int): SessionParameters {
        val frame = fake.sentFrames[index]
        assertEquals(SessionParameters.TYPE_ID, frame.messageTypeId)
        return cbor.decodeFromByteArray(SessionParameters.serializer(), frame.payload)
    }

    /** Records all listener calls for verification. */
    private class RecordingListener : ServerListener {
        val events = mutableListOf<String>()
        var lastHandshakeFailReason: String? = null
        var lastDisconnectMessage: SoftDisconnect? = null
        var lastError: Throwable? = null
        val receivedMessages = mutableListOf<Message>()

        override fun onSessionCreated(session: DeviceSessionSnapshot) {
            events.add("created")
        }

        override fun onSessionActive(session: DeviceSessionSnapshot) {
            events.add("active")
        }

        override fun onMessageReceived(session: DeviceSessionSnapshot, message: Message) {
            events.add("message:${message::class.simpleName}")
            receivedMessages.add(message)
        }

        override fun onSessionHandshakeFailed(session: DeviceSessionSnapshot, reason: String) {
            events.add("handshakeFailed")
            lastHandshakeFailReason = reason
        }

        override fun onSessionDisconnecting(session: DeviceSessionSnapshot, softDisconnect: SoftDisconnect?) {
            events.add("disconnecting")
            lastDisconnectMessage = softDisconnect
        }

        override fun onSessionClosed(session: DeviceSessionSnapshot) {
            events.add("closed")
        }

        override fun onSessionError(session: DeviceSessionSnapshot, error: Throwable) {
            events.add("error")
            lastError = error
        }
    }

    // -- Handshake tests --

    @Test
    fun `handshake success transitions to ACTIVE`() = runTest {
        val (_, transport) = transportWithMessages(deviceIdentity)
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)


        assertEquals(deviceIdentity.identity, session.sessionParameters?.identity)
        assertEquals(deviceIdentity.type, session.sessionParameters?.type)
        server.close()
    }

    @Test
    fun `server sends its own SessionParameters during handshake`() = runTest {
        val (fake, transport) = transportWithMessages(deviceIdentity)
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        server.accept(transport)


        assertTrue(fake.sentFrames.isNotEmpty(), "Server should have sent a reply")
        val reply = decodeSentSessionParameters(fake, 0)
        assertEquals("test-server", reply.identity)
        assertEquals("router", reply.type)
        server.close()
    }

    @Test
    fun `handshake failure when first message is not SessionParameters`() = runTest {
        val listener = RecordingListener()
        val (_, transport) = transportWithMessages(Ping)
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)


        assertEquals(SessionState.CLOSED, session.state)
        assertContains(listener.events, "handshakeFailed")
        assertContains(listener.lastHandshakeFailReason!!, "Ping")
        server.close()
    }

    // -- Parameter tracking tests --

    @Test
    fun `tracks latest SupplyParameters`() = runTest {
        val supply = SupplyParameters(
            voltage = Voltage(400.0),
            current = Current(32.0),
        )
        val (_, transport) = transportWithMessages(deviceIdentity, supply)
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)


        assertNotNull(session.latestSupply)
        assertEquals(400.0, session.latestSupply!!.voltage?.volts)
        assertEquals(32.0, session.latestSupply!!.current?.amperes)
        server.close()
    }

    @Test
    fun `tracks latest DemandParameters`() = runTest {
        val demand = DemandParameters(
            voltage = Voltage(400.0),
            current = Current(16.0),
        )
        val (_, transport) = transportWithMessages(deviceIdentity, demand)
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)


        assertNotNull(session.latestDemand)
        assertEquals(400.0, session.latestDemand!!.voltage?.volts)
        assertEquals(16.0, session.latestDemand!!.current?.amperes)
        server.close()
    }

    @Test
    fun `tracks latest StorageParameters`() = runTest {
        val storage = StorageParameters(
            soc = Percentage(75.0),
            capacity = Energy(50000.0),
        )
        val (_, transport) = transportWithMessages(deviceIdentity, storage)
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)


        assertNotNull(session.latestStorage)
        assertEquals(75.0, session.latestStorage!!.soc?.percent)
        assertEquals(50000.0, session.latestStorage!!.capacity?.wattHours)
        server.close()
    }

    // -- SoftDisconnect tests --

    @Test
    fun `SoftDisconnect transitions session to DISCONNECTING then CLOSED`() = runTest {
        val listener = RecordingListener()
        val softDisconnect = SoftDisconnect(reconnect = true, reason = "update")
        val (_, transport) = transportWithMessages(deviceIdentity, softDisconnect)
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        server.accept(transport)


        assertContains(listener.events, "disconnecting")
        assertContains(listener.events, "closed")
        assertEquals(true, listener.lastDisconnectMessage?.reconnect)
        assertEquals("update", listener.lastDisconnectMessage?.reason)
        server.close()
    }

    // -- Multiple sessions --

    @Test
    fun `multiple sessions tracked independently`() = runTest {
        val device1 = SessionParameters(identity = "device-1")
        val device2 = SessionParameters(identity = "device-2")
        val (_, transport1) = transportWithMessages(device1)
        val (_, transport2) = transportWithMessages(device2)
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session1 = server.accept(transport1)
        val session2 = server.accept(transport2)


        assertNotEquals(session1.id, session2.id)
        assertEquals("device-1", session1.sessionParameters?.identity)
        assertEquals("device-2", session2.sessionParameters?.identity)
        server.close()
    }

    // -- Server close --

    @Test
    fun `server close cleans up all sessions`() = runTest {
        val (fake1, transport1) = transportWithMessages(deviceIdentity)
        val (fake2, transport2) = transportWithMessages(SessionParameters(identity = "device-2"))
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session1 = server.accept(transport1)
        val session2 = server.accept(transport2)


        server.close()

        assertTrue(fake1.closed)
        assertTrue(fake2.closed)
        assertEquals(SessionState.CLOSED, session1.state)
        assertEquals(SessionState.CLOSED, session2.state)
        assertTrue(server.sessions.isEmpty())
    }

    // -- Server disconnect --

    @Test
    fun `disconnect sends SoftDisconnect and closes session`() = runTest {
        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        val server = EpServer(serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)

        // Session should be ACTIVE (channel flow hasn't completed)
        assertEquals(SessionState.ACTIVE, session.state)

        server.disconnect(session, reason = "shutting down")

        // First sent frame is the server's SessionParameters reply, second is SoftDisconnect
        assertTrue(fake.sentFrames.size >= 2, "Expected at least 2 sent frames")
        val disconnectFrame = fake.sentFrames[1]
        assertEquals(SoftDisconnect.TYPE_ID, disconnectFrame.messageTypeId)
        val disconnectMsg = cbor.decodeFromByteArray(SoftDisconnect.serializer(), disconnectFrame.payload)
        assertEquals("shutting down", disconnectMsg.reason)
        assertEquals(false, disconnectMsg.reconnect)
        assertEquals(SessionState.CLOSED, session.state)
        assertFalse(server.sessions.containsKey(session.id))
        server.close()
    }

    // -- Listener callback order --

    @Test
    fun `listener callbacks fire in correct order for successful session`() = runTest {
        val listener = RecordingListener()
        val supply = SupplyParameters(voltage = Voltage(400.0))
        val (_, transport) = transportWithMessages(deviceIdentity, supply)
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        server.accept(transport)


        assertEquals("created", listener.events[0])
        assertEquals("active", listener.events[1])
        assertEquals("message:SupplyParameters", listener.events[2])
        assertContains(listener.events, "closed")
        server.close()
    }

    // -- Empty receive flow --

    @Test
    fun `empty receive flow cleans up session`() = runTest {
        val listener = RecordingListener()
        val (_, transport) = transportWithMessages() // no messages at all
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)


        assertEquals(SessionState.CLOSED, session.state)
        assertContains(listener.events, "closed")
        assertTrue(server.sessions.isEmpty())
        server.close()
    }

    // -- Listener exception swallowed --

    @Test
    fun `listener exception does not crash session`() = runTest {
        val throwingListener = object : ServerListener {
            var activeCallCount = 0

            override fun onSessionCreated(session: DeviceSessionSnapshot) {
                throw RuntimeException("boom on create")
            }

            override fun onSessionActive(session: DeviceSessionSnapshot) {
                activeCallCount++
                throw RuntimeException("boom on active")
            }
        }

        val supply = SupplyParameters(voltage = Voltage(400.0))
        val (_, transport) = transportWithMessages(deviceIdentity, supply)
        val server = EpServer(serverIdentity, throwingListener, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)


        // Session still completed its lifecycle despite listener exceptions
        assertEquals(1, throwingListener.activeCallCount)
        assertNotNull(session.sessionParameters)
        server.close()
    }

    // -- Ping handling --

    @Test
    fun `Ping on active session does not change state`() = runTest {
        val listener = RecordingListener()
        val (_, transport) = transportWithMessages(deviceIdentity, Ping)
        val server = EpServer(serverIdentity, listener, coroutineContext = UnconfinedTestDispatcher())

        val session = server.accept(transport)


        // Ping should be received but not change any parameters
        assertContains(listener.events, "message:Ping")
        assertNull(session.latestSupply)
        assertNull(session.latestDemand)
        assertNull(session.latestStorage)
        server.close()
    }
}
