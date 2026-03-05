package dev.breischl.keneth.core.diagnostics

private var current: DiagnosticCollector? = null

internal actual fun diagnosticContextGet(): DiagnosticCollector? = current

internal actual fun <T> diagnosticContextWith(collector: DiagnosticCollector, block: () -> T): T {
    val previous = current
    current = collector
    try {
        return block()
    } finally {
        current = previous
    }
}
