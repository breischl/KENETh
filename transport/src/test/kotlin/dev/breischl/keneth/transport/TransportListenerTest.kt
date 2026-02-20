package dev.breischl.keneth.transport

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.Ping
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.parsing.ParseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertContains

class TransportListenerTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    private class FakeFrameTransport(
        private val incomingFrames: List<ParseResult<Frame>> = emptyList()
    ) : FrameTransport {
        val sentFrames = mutableListOf<Frame>()

        override suspend fun send(frame: Frame) {
            sentFrames.add(frame)
        }

        override fun receive(): Flow<ParseResult<Frame>> = incomingFrames.asFlow()

        override fun close() {}
    }

    private fun encodeMessage(message: Message): ByteArray {
        @Suppress("UNCHECKED_CAST")
        return cbor.encodeToByteArray(
            message.payloadSerializer as KSerializer<Message>,
            message
        )
    }

    private fun frameFor(message: Message): Frame {
        return Frame(emptyMap(), message.typeId, encodeMessage(message))
    }

    /** Records all listener calls in order. */
    private class RecordingListener : TransportListener {
        val events = mutableListOf<String>()
        var lastSendingMessage: Message? = null
        var lastSendingSnapshot: CborSnapshot? = null
        var lastSentMessage: Message? = null
        var lastReceivedMessage: ReceivedMessage? = null
        var lastReceivedSnapshot: CborSnapshot? = null

        override fun onMessageSending(message: Message, payloadCbor: CborSnapshot) {
            events.add("messageSending")
            lastSendingMessage = message
            lastSendingSnapshot = payloadCbor
        }

        override fun onMessageSent(message: Message) {
            events.add("messageSent")
            lastSentMessage = message
        }

        override fun onMessageReceived(received: ReceivedMessage, payloadCbor: CborSnapshot?) {
            events.add("messageReceived")
            lastReceivedMessage = received
            lastReceivedSnapshot = payloadCbor
        }
    }

    // -- Message send listener tests --

    @Test
    fun `send triggers onMessageSending and onMessageSent`() = runTest {
        val listener = RecordingListener()
        val fake = FakeFrameTransport()
        val mt = MessageTransport(fake, listener = listener)

        mt.send(Ping)

        assertEquals(listOf("messageSending", "messageSent"), listener.events)
        assertEquals(Ping, listener.lastSendingMessage)
        assertEquals(Ping, listener.lastSentMessage)
    }

    @Test
    fun `onMessageSending provides CborSnapshot with correct payload`() = runTest {
        val listener = RecordingListener()
        val fake = FakeFrameTransport()
        val mt = MessageTransport(fake, listener = listener)
        val message = SessionParameters(identity = "test-device")

        mt.send(message)

        val snapshot = listener.lastSendingSnapshot!!
        val expectedPayload = encodeMessage(message)
        assertTrue(expectedPayload.contentEquals(snapshot.rawBytes))
    }

    // -- Message receive listener tests --

    @Test
    fun `receive triggers onMessageReceived with CborSnapshot`() = runTest {
        val listener = RecordingListener()
        val message = SessionParameters(identity = "dev-1", type = "vehicle")
        val frame = frameFor(message)
        val fake = FakeFrameTransport(listOf(ParseResult.success(frame, emptyList())))
        val mt = MessageTransport(fake, listener = listener)

        mt.receive().toList()

        assertEquals(listOf("messageReceived"), listener.events)
        assertTrue(listener.lastReceivedMessage!!.succeeded)
        assertIs<SessionParameters>(listener.lastReceivedMessage!!.message)
        assertTrue(frame.payload.contentEquals(listener.lastReceivedSnapshot!!.rawBytes))
    }

    @Test
    fun `receive with failed frame passes null CborSnapshot`() = runTest {
        val listener = RecordingListener()
        val error = ParseResult.failure<Frame>(emptyList())
        val fake = FakeFrameTransport(listOf(error))
        val mt = MessageTransport(fake, listener = listener)

        mt.receive().toList()

        assertEquals(listOf("messageReceived"), listener.events)
        assertEquals(null, listener.lastReceivedSnapshot)
    }

    // -- Listener exception swallowing --

    @Test
    fun `listener exception is swallowed and transport still works`() = runTest {
        val throwingListener = object : TransportListener {
            override fun onMessageSending(message: Message, payloadCbor: CborSnapshot) {
                throw RuntimeException("boom")
            }

            override fun onMessageSent(message: Message) {
                throw RuntimeException("boom again")
            }
        }
        val fake = FakeFrameTransport()
        val mt = MessageTransport(fake, listener = throwingListener)

        // Should not throw
        mt.send(Ping)

        assertEquals(1, fake.sentFrames.size)
        assertEquals(Ping.typeId, fake.sentFrames[0].messageTypeId)
    }

    // -- CborSnapshot tests --

    @Test
    fun `CborSnapshot hex returns correct hex string`() {
        val bytes = byteArrayOf(0xA1.toByte(), 0x01, 0x02)
        val snapshot = CborSnapshot(bytes)
        assertEquals("a10102", snapshot.hex)
    }

    @Test
    fun `CborSnapshot hex for empty bytes`() {
        val snapshot = CborSnapshot(byteArrayOf())
        assertEquals("", snapshot.hex)
    }

    @Test
    fun `CborSnapshot prettyTree returns OBOR tree format`() {
        // Encode a simple CBOR map: {1: 2}
        val bytes = byteArrayOf(0xA1.toByte(), 0x01, 0x02)
        val snapshot = CborSnapshot(bytes)
        // The prettyTree should contain something representing the map structure
        val tree = snapshot.prettyTree
        assertTrue(tree.isNotEmpty(), "prettyTree should not be empty")
    }

    @Test
    fun `CborSnapshot prettyTree handles invalid CBOR gracefully`() {
        // Invalid CBOR bytes
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte())
        val snapshot = CborSnapshot(bytes)
        assertContains(snapshot.prettyTree, "<decode error:")
    }

    // -- Null listener causes no issues --

    @Test
    fun `null listener does not affect send`() = runTest {
        val fake = FakeFrameTransport()
        val mt = MessageTransport(fake, listener = null)

        mt.send(Ping)

        assertEquals(1, fake.sentFrames.size)
    }

    @Test
    fun `null listener does not affect receive`() = runTest {
        val frame = frameFor(Ping)
        val fake = FakeFrameTransport(listOf(ParseResult.success(frame, emptyList())))
        val mt = MessageTransport(fake, listener = null)

        val results = mt.receive().toList()

        assertEquals(1, results.size)
        assertTrue(results[0].succeeded)
    }
}
