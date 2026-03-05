package dev.breischl.keneth.core.parsing

import dev.breischl.keneth.core.diagnostics.*
import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.UnknownMessage

/**
 * A lenient message parser that handles unknown message types gracefully.
 *
 * This parser will:
 * - Successfully parse known message types
 * - Wrap unknown message types in [dev.breischl.keneth.core.messages.UnknownMessage] with a warning diagnostic
 * - Continue parsing even when non-fatal issues are encountered
 *
 * Use this parser when you need to handle messages from newer protocol versions
 * or when forwarding messages without fully understanding them.
 */
class LenientMessageParser : MessageParser {
    override fun parseMessage(frame: Frame): ParseResult<Message> {
        val collector = DiagnosticCollector()

        val parser = MessageRegistry.parserFor(frame.messageTypeId)
        if (parser == null) {
            collector.warning(
                "UNKNOWN_MESSAGE_TYPE",
                "Unknown message type: 0x${frame.messageTypeId.toString(16)}"
            )
            return ParseResult.success(
                UnknownMessage(frame.messageTypeId, frame.payload),
                collector.diagnostics
            )
        }

        val message = parser(frame.payload, collector)
        return if (message != null) {
            ParseResult.success(message, collector.diagnostics)
        } else {
            ParseResult.failure(collector.diagnostics)
        }
    }
}
