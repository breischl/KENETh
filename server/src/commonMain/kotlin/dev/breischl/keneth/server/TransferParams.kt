package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.DemandParameters
import dev.breischl.keneth.core.messages.StorageParameters
import dev.breischl.keneth.core.messages.SupplyParameters

/**
 * Parameters to publish to a peer during an energy transfer.
 *
 * Each non-null field is sent as a separate EP message on every tick.
 *
 * @property supply Supply parameters to publish, or null to skip.
 * @property demand Demand parameters to publish, or null to skip.
 * @property storage Storage parameters to publish, or null to skip.
 */
data class TransferParams(
    val supply: SupplyParameters? = null,
    val demand: DemandParameters? = null,
    val storage: StorageParameters? = null,
)
