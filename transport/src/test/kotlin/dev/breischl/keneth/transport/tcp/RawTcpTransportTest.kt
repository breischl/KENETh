package dev.breischl.keneth.transport.tcp

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.frames.FrameCodec
import dev.breischl.keneth.transport.TransportTracker
import dev.breischl.keneth.transport.assertFrameEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RawTcpTransportTest {
    private val tracker = TransportTracker()
    private val acceptedSockets = mutableListOf<Socket>()

    @AfterTest
    fun tearDown() {
        acceptedSockets.forEach { runCatching { it.close() } }
        tracker.tearDown()
    }

    private fun startTestServer(): ServerSocket {
        val server = ServerSocket(0) // ephemeral port
        tracker.track(server)
        return server
    }

    private fun createClientTransport(server: ServerSocket): RawTcpClientTransport {
        val transport = RawTcpClientTransport("localhost", server.localPort)
        tracker.clientTransport = transport
        return transport
    }

    private suspend fun acceptConnection(server: ServerSocket): Socket = withContext(Dispatchers.IO) {
        server.accept().also { acceptedSockets.add(it) }
    }

    // -- Isolation tests --

    @Test
    fun `send frame is received by server`() = runTest {
        val server = startTestServer()
        val transport = createClientTransport(server)
        val frame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf())

        val sendJob = launch { transport.send(frame) }
        val accepted = acceptConnection(server)
        sendJob.join()

        val bytes = withContext(Dispatchers.IO) {
            accepted.getInputStream().readNBytes(FrameCodec.encode(frame).size)
        }
        val result = FrameCodec.decode(bytes)
        assertFrameEquals(frame, result)
    }

    @Test
    fun `send frame with payload`() = runTest {
        val server = startTestServer()
        val transport = createClientTransport(server)
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val frame = Frame(emptyMap(), 0x0000_0001u, payload)

        val sendJob = launch { transport.send(frame) }
        val accepted = acceptConnection(server)
        sendJob.join()

        val encoded = FrameCodec.encode(frame)
        val bytes = withContext(Dispatchers.IO) {
            accepted.getInputStream().readNBytes(encoded.size)
        }
        val result = FrameCodec.decode(bytes)
        assertFrameEquals(frame, result)
    }

    @Test
    fun `send frame with empty payload`() = runTest {
        val server = startTestServer()
        val transport = createClientTransport(server)
        val frame = Frame(emptyMap(), 0x0000_0042u, byteArrayOf())

        val sendJob = launch { transport.send(frame) }
        val accepted = acceptConnection(server)
        sendJob.join()

        val encoded = FrameCodec.encode(frame)
        val bytes = withContext(Dispatchers.IO) {
            accepted.getInputStream().readNBytes(encoded.size)
        }
        val result = FrameCodec.decode(bytes)

        assertFrameEquals(frame, result)
        assertEquals(0, result.value!!.payload.size)
    }

    @Test
    fun `receive frame sent by server`() = runTest {
        val server = startTestServer()
        val transport = createClientTransport(server)
        val frame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf())
        val encoded = FrameCodec.encode(frame)

        val receiveDeferred = async { transport.receive().first() }
        val accepted = acceptConnection(server)

        withContext(Dispatchers.IO) {
            accepted.getOutputStream().write(encoded)
            accepted.getOutputStream().flush()
            accepted.shutdownOutput() // Signal EOF so the receive loop breaks
        }

        val result = receiveDeferred.await()
        assertFrameEquals(frame, result)
    }

    @Test
    fun `close completes receive flow`() = runTest {
        val server = startTestServer()
        val transport = createClientTransport(server)

        val receiveDeferred = async { transport.receive().toList() }
        val accepted = acceptConnection(server)

        withContext(Dispatchers.IO) {
            accepted.close()
        }

        val results = receiveDeferred.await()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `send after close throws`() = runTest {
        val server = startTestServer()
        val transport = createClientTransport(server)
        val frame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf())

        // Connect first by sending a frame
        val sendJob = launch { transport.send(frame) }
        val accepted = acceptConnection(server)
        sendJob.join()

        withContext(Dispatchers.IO) {
            accepted.getInputStream().readNBytes(FrameCodec.encode(frame).size)
        }

        // Close server so reconnection attempts fail, then close transport
        server.close()
        transport.close()

        assertFailsWith<Exception> {
            transport.send(frame)
        }
    }

    // -- Integration tests (two RawTcpTransport instances) --

    @Test
    fun `client and server exchange a frame`() = runTest {
        val server = startTestServer()
        val client = createClientTransport(server)
        val frame = Frame(emptyMap(), 0xFFFF_FFFFu, byteArrayOf(0x0A, 0x0B))

        val sendJob = launch { client.send(frame) }
        val accepted = acceptConnection(server)
        val serverSide = RawTcpServerTransport(accepted)
        tracker.serverTransport = serverSide
        sendJob.join()

        // Close client so server's receive flow sees EOF after reading the frame
        client.close()

        val result = serverSide.receive().first()
        assertFrameEquals(frame, result)
    }

    @Test
    fun `server and client exchange a frame`() = runTest {
        val server = startTestServer()
        val client = createClientTransport(server)
        val frame = Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x01, 0x02, 0x03))

        val receiveDeferred = async { client.receive().first() }
        val accepted = acceptConnection(server)
        val serverSide = RawTcpServerTransport(accepted)
        tracker.serverTransport = serverSide

        serverSide.send(frame)
        // Close server so client's receive flow sees EOF after reading the frame
        serverSide.close()

        val result = receiveDeferred.await()
        assertFrameEquals(frame, result)
    }

    @Test
    fun `bidirectional frame exchange`() = runTest {
        val server = startTestServer()
        val client = createClientTransport(server)
        val clientFrame = Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x01))
        val serverFrame = Frame(emptyMap(), 0x0000_0002u, byteArrayOf(0x02))

        // Client sends, then we set up server side
        val sendJob = launch { client.send(clientFrame) }
        val accepted = acceptConnection(server)
        val serverSide = RawTcpServerTransport(accepted)
        tracker.serverTransport = serverSide
        sendJob.join()

        // Server also sends
        serverSide.send(serverFrame)

        // Start both receives
        val serverReceiveDeferred = async { serverSide.receive().first() }
        val clientReceiveDeferred = async { client.receive().first() }

        // Half-close both directions so each receive flow sees EOF after reading its frame
        client.socket?.shutdownOutput()
        accepted.shutdownOutput()

        assertFrameEquals(clientFrame, serverReceiveDeferred.await())
        assertFrameEquals(serverFrame, clientReceiveDeferred.await())
    }

    @Test
    fun `multiple frames in sequence`() = runTest {
        val server = startTestServer()
        val client = createClientTransport(server)
        val frames = listOf(
            Frame(emptyMap(), 0x0000_0001u, byteArrayOf(0x01)),
            Frame(emptyMap(), 0x0000_0002u, byteArrayOf(0x02)),
            Frame(emptyMap(), 0x0000_0003u, byteArrayOf(0x03)),
        )

        val sendJob = launch {
            for (frame in frames) {
                client.send(frame)
            }
        }

        val accepted = acceptConnection(server)
        val serverSide = RawTcpServerTransport(accepted)
        tracker.serverTransport = serverSide
        sendJob.join()

        // Close client so server's receive flow sees EOF after reading all frames
        client.close()

        val received = serverSide.receive().take(frames.size).toList()

        assertEquals(frames.size, received.size)
        for (i in frames.indices) {
            assertFrameEquals(frames[i], received[i])
        }
    }
}
