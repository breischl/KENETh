package dev.breischl.keneth.core.values

/**
 * Represents the electrical isolation status of a DC charging system.
 *
 * Isolation monitoring is critical for DC charging safety. The isolation
 * resistance between the DC system and chassis ground must be maintained
 * above safe thresholds to prevent electric shock hazards.
 * Defined in EnergyNet Protocol section 3.1.5.
 *
 * @property id The CBOR type identifier for this isolation status.
 */
enum class IsolationStatus(val id: UByte) {
    /** Isolation status has not been determined or measured. */
    UNKNOWN(0x00u),

    /** Isolation resistance is within safe operating limits. */
    OK(0x01u),

    /** Isolation resistance is degraded but still above fault threshold. */
    WARNING(0x02u),

    /** Isolation resistance is below the safe threshold; charging should stop. */
    FAULT(0x03u);

    companion object {
        private val byId = entries.associateBy { it.id }

        /**
         * Returns the [IsolationStatus] for the given CBOR type ID, or null if unknown.
         *
         * @param id The CBOR type identifier.
         * @return The corresponding [IsolationStatus], or null if not found.
         */
        fun fromId(id: UByte): IsolationStatus? = byId[id]
    }
}
