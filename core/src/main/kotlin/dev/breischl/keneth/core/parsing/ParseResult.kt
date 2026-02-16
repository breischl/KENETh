package dev.breischl.keneth.core.parsing

import dev.breischl.keneth.core.diagnostics.Diagnostic
import dev.breischl.keneth.core.diagnostics.Severity

/**
 * The result of a parsing operation, containing both the parsed value and any diagnostics.
 *
 * ParseResult supports lenient parsing where the operation can succeed even if
 * warnings were generated. This allows parsers to handle unknown fields or
 * deprecated formats gracefully while still reporting the issues.
 *
 * @param T The type of the parsed value.
 * @property value The parsed value, or null if parsing failed.
 * @property diagnostics A list of diagnostics generated during parsing.
 */
data class ParseResult<T>(
    val value: T?,
    val diagnostics: List<Diagnostic>
) {
    /**
     * True if parsing succeeded (value is non-null).
     *
     * Note: A successful parse may still have warnings in [diagnostics].
     */
    val succeeded: Boolean get() = value != null

    /**
     * True if any diagnostic has ERROR severity.
     */
    val hasErrors: Boolean get() = diagnostics.any { it.severity == Severity.ERROR }

    /**
     * True if any diagnostic has WARNING severity.
     */
    val hasWarnings: Boolean get() = diagnostics.any { it.severity == Severity.WARNING }

    companion object {
        /**
         * Creates a successful result with diagnostics (typically warnings).
         *
         * @param value The parsed value.
         * @param diagnostics The diagnostics to include.
         * @return A successful ParseResult with diagnostics.
         */
        fun <T> success(value: T, diagnostics: List<Diagnostic>): ParseResult<T> =
            ParseResult(value, diagnostics)

        /**
         * Creates a failed result with the given diagnostics.
         *
         * @param diagnostics The error diagnostics explaining the failure.
         * @return A failed ParseResult.
         */
        fun <T> failure(diagnostics: List<Diagnostic>): ParseResult<T> = ParseResult(null, diagnostics)
    }
}