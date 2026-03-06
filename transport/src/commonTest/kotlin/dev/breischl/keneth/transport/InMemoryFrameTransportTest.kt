package dev.breischl.keneth.transport

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Ping
import dev.breischl.keneth.core.parsing.ParseResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryFrameTransportTest {

    private fun testFrame(messageTypeId: UInt = 0u, payload: ByteArray = byteArrayOf()): Frame =
        Frame(emptyMap(), messageTypeId, payload)

    // -- createPair() tests --

    @Test
    fun `frames sent on first transport are received by second`() = runTest {
        val (a, b) = InMemoryFrameTransport.createPair()

        val sent = testFrame(1u, byteArrayOf(0x01, 0x02))
        a.send(sent)

        val received = b.receive().first()
        assertTrue(received.succeeded)
        assertEquals(sent.messageTypeId, received.value!!.messageTypeId)
        assertEquals(sent.payload.toList(), received.value!!.payload.toList())

        a.close()
        b.close()
    }

    @Test
    fun `frames sent on second transport are received by first`() = runTest {
        val (a, b) = InMemoryFrameTransport.createPair()

        val sent = testFrame(2u, byteArrayOf(0xFF.toByte()))
        b.send(sent)

        val received = a.receive().first()
        assertTrue(received.succeeded)
        assertEquals(sent.messageTypeId, received.value!!.messageTypeId)

        a.close()
        b.close()
    }

    @Test
    fun `bidirectional communication works`() = runTest {
        val (a, b) = InMemoryFrameTransport.createPair()

        a.send(testFrame(1u))
        b.send(testFrame(2u))

        assertEquals(1u, b.receive().first().value!!.messageTypeId)
        assertEquals(2u, a.receive().first().value!!.messageTypeId)

        a.close()
        b.close()
    }

    @Test
    fun `closing transport A completes receive flow on transport B`() = runTest {
        val (a, b) = InMemoryFrameTransport.createPair()

        // Closing a closes its outbound channel, which is b's inbound
        a.close()

        val frames = b.receive().toList()
        assertTrue(frames.isEmpty())

        b.close()
    }

    // -- InMemoryPeerConnector tests --

    @Test
    fun `connector local side sends frames received by remoteTransport`() = runTest {
        val connector = InMemoryPeerConnector()
        val local = connector.connect(null)

        local.send(Ping)

        val received = connector.remoteTransport.receive().first()
        assertTrue(received.succeeded)

        local.close()
        connector.remoteTransport.close()
    }

    @Test
    fun `remoteTransport sends frames received by local side`() = runTest {
        val connector = InMemoryPeerConnector()
        val local = connector.connect(null)

        MessageTransport(connector.remoteTransport).send(Ping)

        val received = local.receive().first()
        assertTrue(received.succeeded)

        local.close()
        connector.remoteTransport.close()
    }

    @Test
    fun `listener fires onFrameSending and onFrameSent on local send`() = runTest {
        val connector = InMemoryPeerConnector()

        val sendingEvents = mutableListOf<Frame>()
        val sentEvents = mutableListOf<Frame>()
        val local = connector.connect(object : TransportListener {
            override fun onFrameSending(frame: Frame, encodedBytes: ByteArray) { sendingEvents.add(frame) }
            override fun onFrameSent(frame: Frame, encodedBytes: ByteArray) { sentEvents.add(frame) }
        })

        local.send(Ping)

        assertEquals(1, sendingEvents.size)
        assertEquals(1, sentEvents.size)

        local.close()
        connector.remoteTransport.close()
    }

    @Test
    fun `listener fires onFrameReceived when local receive flow is consumed`() = runTest {
        val connector = InMemoryPeerConnector()

        val receivedEvents = mutableListOf<ParseResult<Frame>>()
        val local = connector.connect(object : TransportListener {
            override fun onFrameReceived(result: ParseResult<Frame>) { receivedEvents.add(result) }
        })

        // Remote sends Ping; consuming local's receive flow fires onFrameReceived on local's listener
        MessageTransport(connector.remoteTransport).send(Ping)
        local.receive().first()

        assertEquals(1, receivedEvents.size)

        local.close()
        connector.remoteTransport.close()
    }

    @Test
    fun `listener fires onDisconnected when local transport is closed`() = runTest {
        val connector = InMemoryPeerConnector()

        var disconnectedFired = false
        val local = connector.connect(object : TransportListener {
            override fun onDisconnected() { disconnectedFired = true }
        })

        local.close()

        assertTrue(disconnectedFired)
        connector.remoteTransport.close()
    }
}
