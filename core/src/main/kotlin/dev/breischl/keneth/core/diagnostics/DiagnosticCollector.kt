package dev.breischl.keneth.core.diagnostics

/**
 * A mutable collector for accumulating diagnostics during parsing.
 *
 * This class provides a convenient way to collect multiple diagnostics
 * during a parsing operation, then retrieve them all at once to create
 * a [dev.breischl.keneth.core.parsing.ParseResult].
 *
 * Example usage:
 * ```kotlin
 * val collector = DiagnosticCollector()
 * collector.warning("UNKNOWN_FIELD", "Unknown field 0x99 ignored")
 * if (isInvalid) {
 *     collector.error("INVALID_VALUE", "Value out of range")
 * }
 * return if (collector.hasErrors()) {
 *     ParseResult.failure(collector.diagnostics)
 * } else {
 *     ParseResult.success(result, collector.diagnostics)
 * }
 * ```
 */
class DiagnosticCollector {
    private val _diagnostics = mutableListOf<Diagnostic>()

    /**
     * Returns an immutable copy of the collected diagnostics.
     */
    val diagnostics: List<Diagnostic> get() = _diagnostics.toList()

    /**
     * Adds a diagnostic to the collection.
     *
     * @param diagnostic The diagnostic to add.
     */
    fun add(diagnostic: Diagnostic) {
        _diagnostics.add(diagnostic)
    }

    /**
     * Adds a warning diagnostic.
     *
     * @param code The machine-readable code identifying the warning type.
     * @param message A human-readable description of the warning.
     * @param byteOffset The byte offset where the issue was detected, if applicable.
     * @param fieldPath The path to the problematic field, if applicable.
     */
    fun warning(code: String, message: String, byteOffset: Int? = null, fieldPath: String? = null) {
        add(Diagnostic(Severity.WARNING, code, message, byteOffset, fieldPath))
    }

    /**
     * Adds an error diagnostic.
     *
     * @param code The machine-readable code identifying the error type.
     * @param message A human-readable description of the error.
     * @param byteOffset The byte offset where the issue was detected, if applicable.
     * @param fieldPath The path to the problematic field, if applicable.
     */
    fun error(code: String, message: String, byteOffset: Int? = null, fieldPath: String? = null) {
        add(Diagnostic(Severity.ERROR, code, message, byteOffset, fieldPath))
    }

    /**
     * Returns true if any collected diagnostic has ERROR severity.
     */
    fun hasErrors(): Boolean = _diagnostics.any { it.severity == Severity.ERROR }
}
