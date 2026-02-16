package dev.breischl.keneth.transport

import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.frames.Frame
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

/**
 * Low-level interface for sending and receiving EnergyNet Protocol [Frame]s.
 *
 * Most callers should use [MessageTransport] instead, which wraps a [FrameTransport]
 * and handles message encoding/decoding automatically. Use [FrameTransport] directly
 * only when you need raw frame access (e.g., forwarding, proxying, or custom encoding).
 *
 * Implementations handle the details of the underlying network transport
 * (TCP, TLS, BLE, etc.).
 *
 * Example usage:
 * ```kotlin
 * val transport = RawTcpClientTransport("charger.local", 56540)
 * try {
 *     transport.send(Frame(emptyMap(), Ping.typeId, byteArrayOf()))
 *
 *     transport.receive().collect { result ->
 *         if (result.succeeded) {
 *             processFrame(result.value!!)
 *         }
 *     }
 * } finally {
 *     transport.close()
 * }
 * ```
 */
interface FrameTransport : Closeable {
    /**
     * Sends a frame over the transport.
     *
     * This is a suspending function that encodes the frame and writes it
     * to the underlying connection. The function returns when the write
     * operation completes.
     *
     * @param frame The frame to send.
     * @throws java.io.IOException if the write operation fails.
     */
    suspend fun send(frame: Frame)

    /**
     * Returns a flow of received frames.
     *
     * The flow emits [ParseResult] values for each frame received. Each result
     * may contain:
     * - A successfully parsed frame with no diagnostics
     * - A successfully parsed frame with warning diagnostics
     * - An error result if the frame could not be parsed
     *
     * The flow completes when the connection is closed or an unrecoverable
     * error occurs.
     *
     * @return A flow of parse results for received frames.
     */
    fun receive(): Flow<ParseResult<Frame>>
}
