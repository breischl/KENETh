package dev.breischl.keneth.transport

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.parsing.ParseResult
import net.orandja.obor.codec.Cbor
import net.orandja.obor.data.CborObject

/**
 * Wraps raw CBOR bytes with lazy hex and pretty-printed representations.
 *
 * Both [hex] and [prettyTree] are computed lazily, so there is no cost
 * if the listener never accesses them.
 */
class CborSnapshot(val rawBytes: ByteArray) {
    /** Hex-encoded representation of the raw CBOR bytes. */
    val hex: String by lazy { rawBytes.joinToString("") { "%02x".format(it) } }

    /** Pretty-printed CBOR tree via OBOR's [CborObject.toString]. */
    val prettyTree: String by lazy {
        try {
            Cbor.decodeFromByteArray(CborObject.serializer(), rawBytes).toString()
        } catch (e: Exception) {
            "<decode error: ${e.message}>"
        }
    }
}

/**
 * Optional callback interface for observing transport activity.
 *
 * All methods have default no-op implementations so callers can override
 * only the events they care about. Exceptions thrown by listener methods
 * are silently swallowed to avoid disrupting transport operation.
 *
 * Pass a listener to transport constructors to enable debug/diagnostic
 * observation of connection lifecycle, frame I/O, and message send/receive.
 */
interface TransportListener {
    // -- Connection lifecycle --

    /** Called before a client transport attempts to connect. */
    fun onConnecting(host: String, port: Int) {}

    /** Called after a connection is successfully established. */
    fun onConnected(host: String, port: Int) {}

    /** Called when the transport is closed. */
    fun onDisconnected() {}

    /** Called when a connection attempt fails. */
    fun onConnectionError(error: Throwable) {}

    // -- Frame layer --

    /** Called before a frame is written to the socket. */
    fun onFrameSending(frame: Frame, encodedBytes: ByteArray) {}

    /** Called after a frame has been flushed to the socket. */
    fun onFrameSent(frame: Frame, encodedBytes: ByteArray) {}

    /** Called after a frame is decoded from the socket. */
    fun onFrameReceived(result: ParseResult<Frame>) {}

    // -- Message layer --

    /** Called after a message is CBOR-encoded but before frame send. */
    fun onMessageSending(message: Message, payloadCbor: CborSnapshot) {}

    /** Called after a message has been sent. */
    fun onMessageSent(message: Message) {}

    /** Called after a message is received and parsed. */
    fun onMessageReceived(received: ReceivedMessage, payloadCbor: CborSnapshot?) {}
}

/**
 * Safely invokes a [TransportListener] callback, swallowing any exception.
 * When the receiver is null, this compiles to a no-op.
 */
internal inline fun TransportListener?.safeNotify(block: TransportListener.() -> Unit) {
    if (this != null) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }
}
