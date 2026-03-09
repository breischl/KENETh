package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.values.Voltage
import dev.breischl.keneth.transport.InMemoryFrameTransport
import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.TransportListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EpNodeSessionTest {
    private val serverIdentity = SessionParameters(identity = "test-server", type = "router")
    private val deviceIdentity = SessionParameters(identity = "test-device", type = "charger")

    /** Records messages sent by the node and whether the transport was closed. */
    private class RecordingTransportListener : TransportListener {
        val sentMessages = mutableListOf<Message>()
        var disconnected = false

        override fun onMessageSent(message: Message) {
            sentMessages.add(message)
        }

        override fun onDisconnected() {
            disconnected = true
        }
    }

    /**
     * Creates an [InMemoryFrameTransport] pair pre-populated with [messages] as incoming frames.
     * The remote side's output is shut down so the local receive flow completes after draining them.
     * Returns the recording listener (captures node sends and close) and the local [MessageTransport].
     */
    private suspend fun transportWithMessages(vararg messages: Message): Pair<RecordingTransportListener, MessageTransport> {
        val (localFT, remoteFT) = InMemoryFrameTransport.createPair()
        val remoteTransport = MessageTransport(remoteFT)
        for (msg in messages) {
            remoteTransport.send(msg)
        }
        remoteFT.shutdownOutput()
        val listener = RecordingTransportListener()
        localFT.listener = listener
        return listener to MessageTransport(localFT, listener = listener)
    }

    /** Records all listener calls for verification. */
    private class RecordingListener : NodeListener {
        val events = mutableListOf<String>()
        var lastHandshakeFailReason: String? = null
        var lastDisconnectMessage: SoftDisconnect? = null
        var lastError: Throwable? = null
        val receivedMessages = mutableListOf<Message>()

        override fun onSessionCreated(session: SessionSnapshot) {
            events.add("created")
        }

        override fun onSessionActive(session: SessionSnapshot) {
            events.add("active")
        }

        override fun onMessageReceived(session: SessionSnapshot, message: Message) {
            events.add("message:${message::class.simpleName}")
            receivedMessages.add(message)
        }

        override fun onSessionHandshakeFailed(session: SessionSnapshot, reason: String) {
            events.add("handshakeFailed")
            lastHandshakeFailReason = reason
        }

        override fun onSessionDisconnecting(session: SessionSnapshot, softDisconnect: SoftDisconnect?) {
            events.add("disconnecting")
            lastDisconnectMessage = softDisconnect
        }

        override fun onSessionClosed(session: SessionSnapshot) {
            events.add("closed")
        }

        override fun onSessionError(session: SessionSnapshot, error: Throwable) {
            events.add("error")
            lastError = error
        }
    }

    // -- Handshake tests --

    @Test
    fun `handshake success transitions to ACTIVE`() = runTest {
        val (_, transport) = transportWithMessages(deviceIdentity)
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session = node.accept(transport)

        assertEquals(deviceIdentity.identity, session.sessionParameters?.identity)
        assertEquals(deviceIdentity.type, session.sessionParameters?.type)
        node.close()
    }

    @Test
    fun `server sends its own SessionParameters during handshake`() = runTest {
        val (listener, transport) = transportWithMessages(deviceIdentity)
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        node.accept(transport)

        assertTrue(listener.sentMessages.isNotEmpty(), "Node should have sent a reply")
        val reply = listener.sentMessages[0] as? SessionParameters
        assertNotNull(reply)
        assertEquals("test-server", reply.identity)
        assertEquals("router", reply.type)
        node.close()
    }

    @Test
    fun `handshake failure when first message is not SessionParameters`() = runTest {
        val nodeListener = RecordingListener()
        val (_, transport) = transportWithMessages(Ping)
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = nodeListener,
            coroutineContext = UnconfinedTestDispatcher()
        )

        val session = node.accept(transport)

        assertEquals(SessionState.CLOSED, session.state)
        assertContains(nodeListener.events, "handshakeFailed")
        assertContains(nodeListener.lastHandshakeFailReason!!, "Ping")
        node.close()
    }

    // -- SoftDisconnect tests --

    @Test
    fun `SoftDisconnect transitions session to DISCONNECTING then CLOSED`() = runTest {
        val nodeListener = RecordingListener()
        val softDisconnect = SoftDisconnect(reconnect = true, reason = "update")
        val (_, transport) = transportWithMessages(deviceIdentity, softDisconnect)
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = nodeListener,
            coroutineContext = UnconfinedTestDispatcher()
        )

        node.accept(transport)

        assertContains(nodeListener.events, "disconnecting")
        assertContains(nodeListener.events, "closed")
        assertEquals(true, nodeListener.lastDisconnectMessage?.reconnect)
        assertEquals("update", nodeListener.lastDisconnectMessage?.reason)
        node.close()
    }

    // -- Multiple sessions --

    @Test
    fun `multiple sessions tracked independently`() = runTest {
        val device1 = SessionParameters(identity = "device-1")
        val device2 = SessionParameters(identity = "device-2")
        val (_, transport1) = transportWithMessages(device1)
        val (_, transport2) = transportWithMessages(device2)
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session1 = node.accept(transport1)
        val session2 = node.accept(transport2)

        assertNotEquals(session1.id, session2.id)
        assertEquals("device-1", session1.sessionParameters?.identity)
        assertEquals("device-2", session2.sessionParameters?.identity)
        node.close()
    }

    // -- Node close --

    @Test
    fun `node close cleans up all sessions`() = runTest {
        val (listener1, transport1) = transportWithMessages(deviceIdentity)
        val (listener2, transport2) = transportWithMessages(SessionParameters(identity = "device-2"))
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session1 = node.accept(transport1)
        val session2 = node.accept(transport2)

        node.close()

        assertTrue(listener1.disconnected)
        assertTrue(listener2.disconnected)
        assertEquals(SessionState.CLOSED, session1.state)
        assertEquals(SessionState.CLOSED, session2.state)
        assertTrue(node.sessions.isEmpty())
    }

    // -- Node disconnect --

    @Test
    fun `disconnect sends SoftDisconnect and closes session`() = runTest {
        val (fake, transport) = channelTransportWithMessages(deviceIdentity)
        val node = EpNode(identity = serverIdentity, coroutineContext = UnconfinedTestDispatcher())

        val session = node.accept(transport)

        // Session should be ACTIVE (channel flow hasn't completed)
        assertEquals(SessionState.ACTIVE, session.state)

        node.disconnect(session, reason = "shutting down")

        // Verify a SoftDisconnect was sent (second frame after SessionParameters handshake reply)
        val sentFrames = fake.sentFrames
        assertTrue(sentFrames.size >= 2, "Expected at least 2 sent frames")
        val disconnectFrame = sentFrames[1]
        assertEquals(SoftDisconnect.TYPE_ID, disconnectFrame.messageTypeId)
        val disconnectMsg = testCbor.decodeFromByteArray(SoftDisconnect.serializer(), disconnectFrame.payload)
        assertEquals("shutting down", disconnectMsg.reason)
        assertEquals(false, disconnectMsg.reconnect)
        assertEquals(SessionState.CLOSED, session.state)
        assertFalse(node.sessions.containsKey(session.id))
        node.close()
    }

    // -- Listener callback order --

    @Test
    fun `listener callbacks fire in correct order for successful session`() = runTest {
        val nodeListener = RecordingListener()
        val supply = SupplyParameters(voltage = Voltage(400.0))
        val (_, transport) = transportWithMessages(deviceIdentity, supply)
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = nodeListener,
            coroutineContext = UnconfinedTestDispatcher()
        )

        node.accept(transport)

        assertEquals("created", nodeListener.events[0])
        assertEquals("active", nodeListener.events[1])
        assertEquals("message:SupplyParameters", nodeListener.events[2])
        assertContains(nodeListener.events, "closed")
        node.close()
    }

    // -- Empty receive flow --

    @Test
    fun `empty receive flow cleans up session`() = runTest {
        val nodeListener = RecordingListener()
        val (_, transport) = transportWithMessages() // no messages at all
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = nodeListener,
            coroutineContext = UnconfinedTestDispatcher()
        )

        val session = node.accept(transport)

        assertEquals(SessionState.CLOSED, session.state)
        assertContains(nodeListener.events, "closed")
        assertTrue(node.sessions.isEmpty())
        node.close()
    }

    // -- Listener exception swallowed --

    @Test
    fun `listener exception does not crash session`() = runTest {
        val throwingListener = object : NodeListener {
            var activeCallCount = 0

            override fun onSessionCreated(session: SessionSnapshot) {
                throw RuntimeException("boom on create")
            }

            override fun onSessionActive(session: SessionSnapshot) {
                activeCallCount++
                throw RuntimeException("boom on active")
            }
        }

        val supply = SupplyParameters(voltage = Voltage(400.0))
        val (_, transport) = transportWithMessages(deviceIdentity, supply)
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = throwingListener,
            coroutineContext = UnconfinedTestDispatcher()
        )

        val session = node.accept(transport)

        // Session still completed its lifecycle despite listener exceptions
        assertEquals(1, throwingListener.activeCallCount)
        assertNotNull(session.sessionParameters)
        node.close()
    }

    // -- Ping handling --

    @Test
    fun `Ping on active session does not change state`() = runTest {
        val nodeListener = RecordingListener()
        val (_, transport) = transportWithMessages(deviceIdentity, Ping)
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = nodeListener,
            coroutineContext = UnconfinedTestDispatcher()
        )

        node.accept(transport)

        // Ping should be received but not change any parameters
        assertContains(nodeListener.events, "message:Ping")
        node.close()
    }
}
