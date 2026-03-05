package dev.breischl.keneth.core.diagnostics

private val threadLocal = ThreadLocal<DiagnosticCollector?>()

internal actual fun diagnosticContextGet(): DiagnosticCollector? = threadLocal.get()

internal actual fun <T> diagnosticContextWith(collector: DiagnosticCollector, block: () -> T): T {
    val previous = threadLocal.get()
    threadLocal.set(collector)
    try {
        return block()
    } finally {
        threadLocal.set(previous)
    }
}
