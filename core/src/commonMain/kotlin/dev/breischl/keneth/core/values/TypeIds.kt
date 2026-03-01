package dev.breischl.keneth.core.values

/**
 * CBOR type identifier constants for EnergyNet Protocol value types.
 *
 * These are the map keys used when encoding EP values as CBOR maps.
 * Defined in EnergyNet Protocol section 3.1.
 */
internal object TypeIds {
    const val TEXT: Int = 0x00         // EP section 3.1 — text string (UTF-8)
    const val FLAG: Int = 0x01         // EP section 3.1 — boolean flag
    const val AMOUNT: Int = 0x02       // EP section 3.1 — monetary amount
    const val TIMESTAMP: Int = 0x03    // EP section 3.1 — UTC timestamp (epoch milliseconds)
    const val BINARY: Int = 0x04       // EP section 3.1 — raw binary data
    const val CURRENCY: Int = 0x05     // EP section 3.1 — ISO 4217 currency code
    const val DURATION: Int = 0x06     // EP section 3.1 — duration (milliseconds)
    const val VOLTAGE: Int = 0x10      // EP section 3.1 — electrical voltage (volts)
    const val CURRENT: Int = 0x11      // EP section 3.1 — electrical current (amperes)
    const val POWER: Int = 0x12        // EP section 3.1 — electrical power (watts)
    const val ENERGY: Int = 0x13       // EP section 3.1 — electrical energy (watt-hours)
    const val PERCENTAGE: Int = 0x14   // EP section 3.1 — percentage (0.0–100.0)
    const val RESISTANCE: Int = 0x15   // EP section 3.1 — electrical resistance (ohms)
    const val BOUNDS: Int = 0x20       // EP section 3.1 — min/max numeric bounds
    const val PRICE_FORECAST: Int = 0x30  // EP section 3.1 — price forecast schedule
    const val SOURCE_MIX: Int = 0x40   // EP section 3.1 — energy source mix
    const val ENERGY_MIX: Int = 0x41   // EP section 3.1 — energy mix breakdown
    const val ISOLATION_STATE: Int = 0x50 // EP section 3.1 — isolation measurement state
}
