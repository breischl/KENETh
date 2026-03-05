package dev.breischl.keneth.transport

import dev.breischl.keneth.core.diagnostics.Diagnostic
import dev.breischl.keneth.core.diagnostics.Severity
import dev.breischl.keneth.core.messages.Message

/**
 * The result of receiving a message from a [MessageTransport].
 *
 * On success, [message] is non-null and [diagnostics] may contain warnings.
 * On failure, [message] is null and [diagnostics] contains the errors.
 * [headers] are available whenever the frame was successfully decoded,
 * even if message parsing failed.
 *
 * @property message The parsed message, or null if parsing failed.
 * @property headers The frame headers. Empty if the frame itself could not be decoded.
 * @property diagnostics Warnings or errors from frame decoding and message parsing.
 */
data class ReceivedMessage(
    val message: Message?,
    val headers: Map<UInt, Any>,
    val diagnostics: List<Diagnostic>
) {
    val succeeded: Boolean get() = message != null
    val hasErrors: Boolean get() = diagnostics.any { it.severity == Severity.ERROR }
    val hasWarnings: Boolean get() = diagnostics.any { it.severity == Severity.WARNING }
}