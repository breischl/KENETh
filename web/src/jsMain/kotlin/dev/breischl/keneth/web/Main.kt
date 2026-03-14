package dev.breischl.keneth.web

import dev.breischl.keneth.web.debugger.initDebugger
import dev.breischl.keneth.web.demo.initNodeDemo
import kotlinx.browser.document

/**
 * Entry point for all web pages. Dispatches to the appropriate page initializer
 * based on which container element ID is present in the DOM.
 */
fun main() {
    when {
        document.getElementById("keneth-demo") != null -> initNodeDemo()
        document.getElementById("keneth-message-debugger") != null -> initDebugger()
        else -> console.error("No recognized page container found (expected #keneth-demo or #keneth-message-debugger)")
    }
}
