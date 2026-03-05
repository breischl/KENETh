package dev.breischl.keneth.core.parsing

import dev.breischl.keneth.core.diagnostics.Severity
import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.Message

/**
 * A strict message parser that treats any diagnostic as a failure.
 *
 * This parser wraps a [LenientMessageParser] and converts any warnings
 * to errors, causing the parse to fail if any issues are detected.
 *
 * Use this parser for conformance testing or when you need to ensure
 * that messages exactly match the expected format.
 *
 * @param lenientParser The underlying lenient parser to use.
 */
class StrictMessageParser(
    private val lenientParser: LenientMessageParser = LenientMessageParser()
) : MessageParser {
    override fun parseMessage(frame: Frame): ParseResult<Message> {
        val result = lenientParser.parseMessage(frame)

        // In strict mode, any diagnostic (warning or error) is treated as failure
        if (result.diagnostics.isNotEmpty()) {
            val errorDiagnostics = result.diagnostics.map { diagnostic ->
                if (diagnostic.severity == Severity.WARNING) {
                    diagnostic.copy(severity = Severity.ERROR)
                } else {
                    diagnostic
                }
            }
            return ParseResult.failure(errorDiagnostics)
        }

        return result
    }
}
