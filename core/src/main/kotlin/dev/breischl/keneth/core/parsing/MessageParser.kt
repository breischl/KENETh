package dev.breischl.keneth.core.parsing

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message

/**
 * Interface for parsing frames into typed messages.
 *
 * Implementations may have different strictness levels for handling
 * unknown message types or malformed payloads.
 */
interface MessageParser {
    /**
     * Parses a frame into a typed message.
     *
     * @param frame The frame to parse.
     * @return A ParseResult containing the parsed message or error diagnostics.
     */
    fun parseMessage(frame: Frame): ParseResult<Message>
}
