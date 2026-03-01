package dev.breischl.keneth.core.values

/**
 * CBOR type identifier constants for EnergyNet Protocol value types.
 *
 * These are the map keys used when encoding EP values as CBOR maps.
 * Defined in EnergyNet Protocol section 3.1.
 */
internal object TypeIds {
    const val TEXT: Int = 0x00
    const val FLAG: Int = 0x01
    const val AMOUNT: Int = 0x02
    const val TIMESTAMP: Int = 0x03
    const val BINARY: Int = 0x04
    const val CURRENCY: Int = 0x05
    const val DURATION: Int = 0x06
    const val VOLTAGE: Int = 0x10
    const val CURRENT: Int = 0x11
    const val POWER: Int = 0x12
    const val ENERGY: Int = 0x13
    const val PERCENTAGE: Int = 0x14
    const val RESISTANCE: Int = 0x15
    const val BOUNDS: Int = 0x20
    const val PRICE_FORECAST: Int = 0x30
    const val SOURCE_MIX: Int = 0x40
    const val ENERGY_MIX: Int = 0x41
    const val ISOLATION_STATE: Int = 0x50
}
