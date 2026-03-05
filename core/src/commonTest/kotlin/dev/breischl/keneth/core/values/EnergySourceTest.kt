package dev.breischl.keneth.core.values

import kotlin.test.Test
import kotlin.test.assertEquals

class EnergySourceTest {

    @Test
    fun `EnergySource fromId returns correct values`() {
        assertEquals(EnergySource.WIND, EnergySource.fromId(0x01u))
        assertEquals(EnergySource.SOLAR, EnergySource.fromId(0x02u))
        assertEquals(EnergySource.LOCAL_SOLAR, EnergySource.fromId(0x09u))
        assertEquals(null, EnergySource.fromId(0xFFu))
    }
}
