package dev.breischl.keneth.transport

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.frames.FrameCodec
import dev.breischl.keneth.core.parsing.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Socket

/**
 * Abstract base class for socket-based transports.
 *
 * Handles frame encoding/decoding and I/O over a [Socket] (including [javax.net.ssl.SSLSocket]).
 * Subclasses provide socket creation/lifecycle via [socket].
 */
abstract class SocketTransport : FrameTransport {
    companion object {
        /** The default EnergyNet Protocol port. */
        const val DEFAULT_PORT = 56540
    }

    internal var socket: Socket? = null
    protected val lock = Any()

    protected abstract fun socket(): Socket

    override suspend fun send(frame: Frame) {
        withContext(Dispatchers.IO) {
            val socket = socket()
            val bytes = FrameCodec.encode(frame)
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        }
    }

    override fun receive(): Flow<ParseResult<Frame>> = flow {
        val socket = socket()
        val inputStream = socket.getInputStream()

        try {
            while (!socket.isClosed) {
                val result = FrameCodec.decodeFromStream(inputStream) ?: break
                emit(result)
            }
        } catch (_: IOException) {
            // Socket was closed (locally or remotely) while blocked on read.
            // SocketException for plain TCP, SSLException for TLS — both are IOExceptions.
            // Complete the flow normally rather than propagating the exception.
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        synchronized(lock) {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
            socket = null
        }
    }
}
