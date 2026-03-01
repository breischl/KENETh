package dev.breischl.keneth.core.values

import kotlin.test.Test
import kotlin.test.assertEquals

class IsolationStatusTest {

    @Test
    fun `IsolationStatus fromId returns correct values`() {
        assertEquals(IsolationStatus.UNKNOWN, IsolationStatus.fromId(0x00u))
        assertEquals(IsolationStatus.OK, IsolationStatus.fromId(0x01u))
        assertEquals(IsolationStatus.WARNING, IsolationStatus.fromId(0x02u))
        assertEquals(IsolationStatus.FAULT, IsolationStatus.fromId(0x03u))
        assertEquals(null, IsolationStatus.fromId(0xFFu))
    }
}
