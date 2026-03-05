package dev.breischl.keneth.core.diagnostics

/**
 * Thread/context bridge for passing a [DiagnosticCollector] to serializers
 * during CBOR deserialization.
 *
 * CBOR decoding is synchronous, so a context variable is a safe mechanism to
 * make the collector available to nested [kotlinx.serialization.KSerializer]
 * implementations without modifying the [kotlinx.serialization.encoding.Decoder] interface.
 *
 * Platform-specific: JVM uses `ThreadLocal`; JS uses a plain `var` (single-threaded);
 * Native uses a `@ThreadLocal` top-level property (one instance per OS thread).
 */
object DiagnosticContext {
    /**
     * Returns the [DiagnosticCollector] for the current decode operation,
     * or null if none is active.
     */
    fun get(): DiagnosticCollector? = diagnosticContextGet()

    /**
     * Executes [block] with the given [collector] available via [get].
     *
     * Restores the previous collector (if any) when [block] completes,
     * supporting safe nesting.
     */
    fun <T> withCollector(collector: DiagnosticCollector, block: () -> T): T =
        diagnosticContextWith(collector, block)
}

internal expect fun diagnosticContextGet(): DiagnosticCollector?
internal expect fun <T> diagnosticContextWith(collector: DiagnosticCollector, block: () -> T): T
