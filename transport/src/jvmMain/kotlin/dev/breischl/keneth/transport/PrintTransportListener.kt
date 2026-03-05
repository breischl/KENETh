package dev.breischl.keneth.transport

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.parsing.ParseResult
import java.io.PrintStream

/**
 * A [TransportListener] that prints all transport events to a [PrintStream].
 *
 * Useful for development and debugging — pass to transport constructors to see
 * connection lifecycle, frame I/O, and message activity on the console.
 *
 * Example:
 * ```kotlin
 * val listener = PrintTransportListener()
 * val frameTransport = RawTcpClientTransport("charger.local", listener = listener)
 * val transport = MessageTransport(frameTransport, listener = listener)
 * ```
 *
 * @param out The output stream to print to (default: [System.out]).
 * @param prefix A prefix prepended to every line (default: `"[transport] "`).
 */
class PrintTransportListener(
    private val out: PrintStream = System.out,
    private val prefix: String = "[transport] "
) : TransportListener {

    private fun log(msg: String) {
        out.println("$prefix$msg")
    }

    // -- Connection lifecycle --

    override fun onConnecting(host: String, port: Int) {
        log("Connecting to $host:$port...")
    }

    override fun onConnected(host: String, port: Int) {
        log("Connected to $host:$port")
    }

    override fun onDisconnected() {
        log("Disconnected")
    }

    override fun onConnectionError(error: Throwable) {
        log("Connection error: ${error::class.simpleName}: ${error.message}")
    }

    // -- Frame layer --

    override fun onFrameSending(frame: Frame, encodedBytes: ByteArray) {
        log(
            "Sending frame: typeId=0x${frame.messageTypeId.toString(16)}, " +
                    "payload=${frame.payload.size} bytes, " +
                    "wire=${encodedBytes.size} bytes"
        )
    }

    override fun onFrameSent(frame: Frame, encodedBytes: ByteArray) {
        log("Frame sent: typeId=0x${frame.messageTypeId.toString(16)}")
    }

    override fun onFrameReceived(result: ParseResult<Frame>) {
        if (result.succeeded) {
            val frame = result.value!!
            log(
                "Frame received: typeId=0x${frame.messageTypeId.toString(16)}, " +
                        "payload=${frame.payload.size} bytes"
            )
        } else {
            log("Frame receive error: ${result.diagnostics}")
        }
    }

    // -- Message layer --

    override fun onMessageSending(message: Message, payloadCbor: CborSnapshot) {
        log("Sending ${message::class.simpleName} (typeId=0x${message.typeId.toString(16)})")
        log("  CBOR hex: ${payloadCbor.hex}")
        log("  CBOR tree: ${payloadCbor.prettyTree}")
    }

    override fun onMessageSent(message: Message) {
        log("Sent ${message::class.simpleName}")
    }

    override fun onMessageReceived(received: ReceivedMessage, payloadCbor: CborSnapshot?) {
        if (received.succeeded) {
            val msg = received.message!!
            log("Received ${msg::class.simpleName} (typeId=0x${msg.typeId.toString(16)})")
            if (payloadCbor != null) {
                log("  CBOR hex: ${payloadCbor.hex}")
                log("  CBOR tree: ${payloadCbor.prettyTree}")
            }
        } else {
            log("Received message error: ${received.diagnostics}")
        }
        if (received.hasWarnings) {
            log("  Warnings: ${received.diagnostics.filter { it.severity == dev.breischl.keneth.core.diagnostics.Severity.WARNING }}")
        }
    }
}
