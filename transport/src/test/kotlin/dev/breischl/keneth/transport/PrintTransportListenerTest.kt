package dev.breischl.keneth.transport

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.Ping
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.transport.tcp.RawTcpClientTransport
import dev.breischl.keneth.transport.tcp.RawTcpServerTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PrintTransportListenerTest {
    private val tracker = TransportTracker()

    @AfterTest
    fun tearDown() {
        tracker.tearDown()
    }

    @Test
    fun `prints connection lifecycle events`() = runTest {
        val server = ServerSocket(0).also { tracker.track(it) }
        val baos = ByteArrayOutputStream()
        val listener = PrintTransportListener(out = PrintStream(baos))

        val client = RawTcpClientTransport("localhost", server.localPort, listener = listener)
        tracker.clientTransport = client

        val sendJob = launch { client.send(Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf())) }
        val accepted = withContext(Dispatchers.IO) { server.accept() }
        val serverSide = RawTcpServerTransport(accepted)
        tracker.serverTransport = serverSide
        sendJob.join()

        client.close()

        val output = baos.toString()
        assertContains(output, "Connecting to localhost:")
        assertContains(output, "Connected to localhost:")
        assertContains(output, "Disconnected")
    }

    @Test
    fun `prints frame send and receive events`() = runTest {
        val server = ServerSocket(0).also { tracker.track(it) }
        val baos = ByteArrayOutputStream()
        val listener = PrintTransportListener(out = PrintStream(baos))

        val client = RawTcpClientTransport("localhost", server.localPort, listener = listener)
        tracker.clientTransport = client

        val frame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf(0x01, 0x02))

        val sendJob = launch { client.send(frame) }
        val accepted = withContext(Dispatchers.IO) { server.accept() }
        tracker.serverTransport = RawTcpServerTransport(accepted)
        sendJob.join()

        val output = baos.toString()
        assertContains(output, "Sending frame: typeId=0xffffffff")
        assertContains(output, "payload=2 bytes")
        assertContains(output, "Frame sent: typeId=0xffffffff")
    }

    @Test
    fun `prints message send and receive events with CBOR details`() = runTest {
        val cbor = Cbor { ingnoreUnknownKeys = true }
        val baos = ByteArrayOutputStream()
        val listener = PrintTransportListener(out = PrintStream(baos))

        val message = SessionParameters(identity = "vehicle-1", type = "charger")

        @Suppress("UNCHECKED_CAST")
        val payload = cbor.encodeToByteArray(
            message.payloadSerializer as KSerializer<Message>,
            message
        )
        val frame = Frame(emptyMap(), message.typeId, payload)
        val fakeTransport = object : FrameTransport {
            val sentFrames = mutableListOf<Frame>()
            override suspend fun send(frame: Frame) {
                sentFrames.add(frame)
            }

            override fun receive() = kotlinx.coroutines.flow.flowOf(ParseResult.success(frame, emptyList()))
            override fun close() {}
        }

        val mt = MessageTransport(fakeTransport, listener = listener)
        mt.send(message)
        mt.receive().toList()

        val output = baos.toString()
        assertContains(output, "Sending SessionParameters")
        assertContains(output, "CBOR hex:")
        assertContains(output, "CBOR tree:")
        assertContains(output, "Sent SessionParameters")
        assertContains(output, "Received SessionParameters")
    }

    @Test
    fun `generates example output`() = runTest {
        val server = ServerSocket(0).also { tracker.track(it) }
        val baos = ByteArrayOutputStream()
        val listener = PrintTransportListener(out = PrintStream(baos))

        // Client sends two messages; server receives them
        val client = RawTcpClientTransport("localhost", server.localPort, listener = listener)
        tracker.clientTransport = client
        val clientMessages = MessageTransport(client, listener = listener)

        // Send messages
        val sendJob = launch {
            clientMessages.send(SessionParameters(identity = "vehicle-1", type = "charger"))
            clientMessages.send(Ping)
        }

        val accepted = withContext(Dispatchers.IO) { server.accept() }
        val serverFrameTransport = RawTcpServerTransport(accepted, listener = listener)
        tracker.serverTransport = serverFrameTransport
        val serverMessages = MessageTransport(serverFrameTransport, listener = listener)

        sendJob.join()

        // Close client so server receives EOF
        clientMessages.close()

        // Server receives both messages
        val received = serverMessages.receive().toList()
        assertTrue(received.size == 2)

        val output = baos.toString()

        // Verify key events are present
        assertContains(output, "Connecting")
        assertContains(output, "Connected")
        assertContains(output, "SessionParameters")
        assertContains(output, "Ping")
        assertContains(output, "Disconnected")
    }
}
