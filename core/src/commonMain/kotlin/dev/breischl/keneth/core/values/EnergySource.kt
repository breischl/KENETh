package dev.breischl.keneth.core.values

/**
 * Represents the source of electrical energy in the power grid.
 *
 * This enum is used in [SourceMix] and [EnergyMix] to describe the composition
 * of energy by generation source. Sources prefixed with "LOCAL_" indicate
 * on-site generation (e.g., rooftop solar).
 * Defined in EnergyNet Protocol section 3.1.3.
 *
 * @property id The CBOR type identifier for this energy source.
 */
enum class EnergySource(val id: UByte) {
    /** Wind turbine generation (grid-scale). */
    WIND(0x01u),

    /** Solar photovoltaic generation (grid-scale). */
    SOLAR(0x02u),

    /** Hydroelectric generation. */
    HYDRO(0x03u),

    /** Nuclear power generation. */
    NUCLEAR(0x04u),

    /** Natural gas power generation. */
    GAS(0x05u),

    /** Oil/petroleum power generation. */
    OIL(0x06u),

    /** Coal power generation. */
    COAL(0x07u),

    /** Local/on-site wind generation (e.g., small turbine). */
    LOCAL_WIND(0x08u),

    /** Local/on-site solar generation (e.g., rooftop PV). */
    LOCAL_SOLAR(0x09u);

    companion object {
        private val byId = entries.associateBy { it.id }

        /**
         * Returns the [EnergySource] for the given CBOR type ID, or null if unknown.
         *
         * @param id The CBOR type identifier.
         * @return The corresponding [EnergySource], or null if not found.
         */
        fun fromId(id: UByte): EnergySource? = byId[id]
    }
}
