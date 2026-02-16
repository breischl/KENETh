package dev.breischl.keneth.core.diagnostics

/**
 * A diagnostic message generated during parsing.
 *
 * Diagnostics provide detailed information about issues encountered while
 * parsing frames or messages. They include machine-readable codes for
 * programmatic handling and human-readable messages for debugging.
 *
 * @property severity The severity level (WARNING or ERROR).
 * @property code A machine-readable code identifying the issue type
 *                (e.g., "INVALID_MAGIC", "UNKNOWN_MESSAGE_TYPE").
 * @property message A human-readable description of the issue.
 * @property byteOffset The byte offset in the input where the issue was detected, if applicable.
 * @property fieldPath The path to the field that caused the issue (e.g., "message.voltage"), if applicable.
 */
data class Diagnostic(
    val severity: Severity,
    val code: String,
    val message: String,
    val byteOffset: Int? = null,
    val fieldPath: String? = null
)
