package dev.breischl.keneth.transport

/**
 * Safely invokes a callback on a nullable receiver, swallowing any exception.
 * When the receiver is null, this is a no-op. Exceptions thrown by [block] are silently swallowed.
 */
inline fun <T : Any> T?.safeNotify(block: T.() -> Unit) {
    if (this != null) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }
}
