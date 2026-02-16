package dev.breischl.keneth.core.diagnostics

import dev.breischl.keneth.core.parsing.ParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsTest {

    @Test
    fun `ParseResult success with no diagnostics`() {
        val result = ParseResult.success("test value", listOf())

        assertTrue(result.succeeded)
        assertFalse(result.hasErrors)
        assertFalse(result.hasWarnings)
        assertEquals("test value", result.value)
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `ParseResult success with warnings`() {
        val warning = Diagnostic(Severity.WARNING, "WARN_001", "A warning")
        val result = ParseResult.success("test value", listOf(warning))

        assertTrue(result.succeeded)
        assertFalse(result.hasErrors)
        assertTrue(result.hasWarnings)
        assertEquals("test value", result.value)
        assertEquals(1, result.diagnostics.size)
    }

    @Test
    fun `ParseResult failure with errors`() {
        val error = Diagnostic(Severity.ERROR, "ERR_001", "An error")
        val result = ParseResult.failure<String>(listOf(error))

        assertFalse(result.succeeded)
        assertTrue(result.hasErrors)
        assertFalse(result.hasWarnings)
        assertEquals(null, result.value)
    }

    @Test
    fun `DiagnosticCollector collects warnings and errors`() {
        val collector = DiagnosticCollector()

        collector.warning("WARN_001", "First warning")
        collector.error("ERR_001", "First error")
        collector.warning("WARN_002", "Second warning", byteOffset = 10)
        collector.error("ERR_002", "Second error", fieldPath = "field.path")

        assertEquals(4, collector.diagnostics.size)
        assertTrue(collector.hasErrors())

        val warnings = collector.diagnostics.filter { it.severity == Severity.WARNING }
        val errors = collector.diagnostics.filter { it.severity == Severity.ERROR }

        assertEquals(2, warnings.size)
        assertEquals(2, errors.size)

        assertEquals(10, warnings[1].byteOffset)
        assertEquals("field.path", errors[1].fieldPath)
    }

    @Test
    fun `Diagnostic contains all provided fields`() {
        val diagnostic = Diagnostic(
            severity = Severity.ERROR,
            code = "TEST_CODE",
            message = "Test message",
            byteOffset = 42,
            fieldPath = "root.child"
        )

        assertEquals(Severity.ERROR, diagnostic.severity)
        assertEquals("TEST_CODE", diagnostic.code)
        assertEquals("Test message", diagnostic.message)
        assertEquals(42, diagnostic.byteOffset)
        assertEquals("root.child", diagnostic.fieldPath)
    }
}
