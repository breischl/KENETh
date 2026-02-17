package dev.breischl.keneth.core.diagnostics

/**
 * Thread-local bridge for passing a [DiagnosticCollector] to serializers
 * during CBOR deserialization.
 *
 * CBOR decoding is synchronous and single-threaded, so a thread-local
 * is a safe mechanism to make the collector available to nested
 * [kotlinx.serialization.KSerializer] implementations without modifying
 * the [kotlinx.serialization.encoding.Decoder] interface.
 */
object DiagnosticContext {
    private val threadLocal = ThreadLocal<DiagnosticCollector?>()

    /**
     * Returns the [DiagnosticCollector] for the current decode operation,
     * or null if none is active.
     */
    fun get(): DiagnosticCollector? = threadLocal.get()

    /**
     * Executes [block] with the given [collector] available via [get].
     *
     * Restores the previous collector (if any) when [block] completes,
     * supporting safe nesting.
     */
    fun <T> withCollector(collector: DiagnosticCollector, block: () -> T): T {
        val previous = threadLocal.get()
        threadLocal.set(collector)
        try {
            return block()
        } finally {
            threadLocal.set(previous)
        }
    }
}
