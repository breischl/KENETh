package dev.breischl.keneth.transport

import dev.breischl.keneth.core.diagnostics.Diagnostic
import dev.breischl.keneth.core.diagnostics.Severity
import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.parsing.LenientMessageParser
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.parsing.StrictMessageParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import kotlin.test.*

class MessageTransportTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    private class FakeTransport(
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

    private fun frameFor(message: Message, headers: Map<UInt, Any> = emptyMap()): Frame {
        return Frame(headers, message.typeId, encodeMessage(message))
    }

    // -- Send tests --

    @Test
    fun `send encodes Ping into frame`() = runTest {
        val fake = FakeTransport()
        val mt = MessageTransport(fake)

        mt.send(Ping)

        assertEquals(1, fake.sentFrames.size)
        assertEquals(Ping.typeId, fake.sentFrames[0].messageTypeId)
    }

    @Test
    fun `send encodes SessionParameters with correct payload`() = runTest {
        val fake = FakeTransport()
        val mt = MessageTransport(fake)
        val message = SessionParameters(identity = "test-device", type = "charger")

        mt.send(message)

        assertEquals(1, fake.sentFrames.size)
        val frame = fake.sentFrames[0]
        assertEquals(message.typeId, frame.messageTypeId)

        val decoded = cbor.decodeFromByteArray(SessionParameters.serializer(), frame.payload)
        assertEquals("test-device", decoded.identity)
        assertEquals("charger", decoded.type)
    }

    @Test
    fun `send passes headers to frame`() = runTest {
        val fake = FakeTransport()
        val mt = MessageTransport(fake)
        val headers = mapOf<UInt, Any>(1u to "value1", 2u to "value2")

        mt.send(Ping, headers)

        assertEquals(headers, fake.sentFrames[0].headers)
    }

    @Test
    fun `send defaults to empty headers`() = runTest {
        val fake = FakeTransport()
        val mt = MessageTransport(fake)

        mt.send(Ping)

        assertTrue(fake.sentFrames[0].headers.isEmpty())
    }

    // -- Receive tests --

    @Test
    fun `receive parses Ping frame`() = runTest {
        val frame = frameFor(Ping)
        val fake = FakeTransport(listOf(ParseResult.success(frame, emptyList())))
        val mt = MessageTransport(fake)

        val results = mt.receive().toList()

        assertEquals(1, results.size)
        assertTrue(results[0].succeeded)
        assertEquals(Ping, results[0].message)
    }

    @Test
    fun `receive parses SessionParameters frame`() = runTest {
        val original = SessionParameters(
            identity = "device-1",
            type = "vehicle",
            version = "1.0"
        )
        val fake = FakeTransport(listOf(ParseResult.success(frameFor(original), emptyList())))
        val mt = MessageTransport(fake)

        val results = mt.receive().toList()

        assertEquals(1, results.size)
        assertTrue(results[0].succeeded)
        val received = results[0].message
        assertIs<SessionParameters>(received)
        assertEquals("device-1", received.identity)
        assertEquals("vehicle", received.type)
        assertEquals("1.0", received.version)
    }

    @Test
    fun `receive preserves frame headers`() = runTest {
        val headers = mapOf<UInt, Any>(1u to "val")
        val frame = Frame(headers, Ping.typeId, encodeMessage(Ping))
        val fake = FakeTransport(listOf(ParseResult.success(frame, emptyList())))
        val mt = MessageTransport(fake)

        val results = mt.receive().toList()

        assertEquals(headers, results[0].headers)
    }

    @Test
    fun `receive propagates frame parse errors`() = runTest {
        val error = ParseResult.failure<Frame>(
            listOf(Diagnostic(Severity.ERROR, "INVALID_MAGIC", "bad magic"))
        )
        val fake = FakeTransport(listOf(error))
        val mt = MessageTransport(fake)

        val results = mt.receive().toList()

        assertEquals(1, results.size)
        assertFalse(results[0].succeeded)
        assertTrue(results[0].hasErrors)
        assertEquals("INVALID_MAGIC", results[0].diagnostics[0].code)
    }

    @Test
    fun `receive handles unknown message type leniently`() = runTest {
        val unknownTypeId = 0xDEAD_BEEFu
        val payload = byteArrayOf(0x01, 0x02)
        val frame = Frame(emptyMap(), unknownTypeId, payload)
        val fake = FakeTransport(listOf(ParseResult.success(frame, emptyList())))
        val mt = MessageTransport(fake, LenientMessageParser())

        val results = mt.receive().toList()

        assertEquals(1, results.size)
        assertTrue(results[0].succeeded)
        assertTrue(results[0].hasWarnings)
        val msg = results[0].message
        assertIs<UnknownMessage>(msg)
        assertEquals(unknownTypeId, msg.typeId)
    }

    @Test
    fun `receive with strict parser rejects unknown message type`() = runTest {
        val frame = Frame(emptyMap(), 0xDEAD_BEEFu, byteArrayOf())
        val fake = FakeTransport(listOf(ParseResult.success(frame, emptyList())))
        val mt = MessageTransport(fake, StrictMessageParser())

        val results = mt.receive().toList()

        assertEquals(1, results.size)
        assertFalse(results[0].succeeded)
        assertTrue(results[0].hasErrors)
    }

    @Test
    fun `receive combines frame and message diagnostics`() = runTest {
        val frame = frameFor(Ping)
        val frameWarning = Diagnostic(Severity.WARNING, "EXTRA_BYTES", "extra bytes after payload")
        val fake = FakeTransport(listOf(ParseResult.success(frame, listOf(frameWarning))))
        val mt = MessageTransport(fake)

        val results = mt.receive().toList()

        assertTrue(results[0].succeeded)
        assertTrue(results[0].diagnostics.any { it.code == "EXTRA_BYTES" })
    }

    @Test
    fun `receive handles multiple frames`() = runTest {
        val messages = listOf(
            Ping,
            SessionParameters(identity = "dev-1"),
            SessionParameters(identity = "dev-2", type = "charger")
        )
        val frames = messages.map { ParseResult.success(frameFor(it), emptyList()) }
        val fake = FakeTransport(frames)
        val mt = MessageTransport(fake)

        val results = mt.receive().toList()

        assertEquals(3, results.size)
        assertTrue(results.all { it.succeeded })
        assertEquals(Ping, results[0].message)
        assertIs<SessionParameters>(results[1].message)
        assertEquals("dev-1", (results[1].message as SessionParameters).identity)
        assertEquals("dev-2", (results[2].message as SessionParameters).identity)
    }

    // -- Close test --

    @Test
    fun `close delegates to underlying transport`() {
        val fake = FakeTransport()
        val mt = MessageTransport(fake)

        mt.close()

        assertTrue(fake.closed)
    }
}
