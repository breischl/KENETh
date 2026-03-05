package dev.breischl.keneth.transport

import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.frames.Frame
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tracks transports and server sockets for cleanup in @AfterTest.
 */
class TransportTracker {
    var clientTransport: SocketTransport? = null
    var serverTransport: SocketTransport? = null
    private val closeables = mutableListOf<AutoCloseable>()

    fun track(closeable: AutoCloseable) {
        closeables.add(closeable)
    }

    fun tearDown() {
        clientTransport?.close()
        serverTransport?.close()
        closeables.forEach { runCatching { it.close() } }
    }
}

/**
 * Asserts that a ParseResult contains a successfully decoded frame matching the expected frame.
 */
fun assertFrameEquals(expected: Frame, result: ParseResult<Frame>) {
    assertTrue(result.succeeded, "Expected successful parse but got errors: ${result.diagnostics}")
    assertEquals(expected.messageTypeId, result.value!!.messageTypeId)
    assertTrue(
        result.value!!.payload.contentEquals(expected.payload),
        "Payload mismatch"
    )
}
