package dev.breischl.keneth.core.diagnostics

/**
 * Severity level for parsing diagnostics.
 *
 * Diagnostics are generated during parsing to report issues found in the
 * input data. The severity indicates how the issue should be handled.
 */
enum class Severity {
    /**
     * A non-fatal issue that allows parsing to continue.
     *
     * Warnings indicate problems like unknown fields, deprecated formats,
     * or recoverable data issues. The parsed result is still usable.
     */
    WARNING,

    /**
     * A fatal issue that prevents successful parsing.
     *
     * Errors indicate problems like malformed data, missing required fields,
     * or protocol violations. The parse operation fails with this diagnostic.
     */
    ERROR
}
