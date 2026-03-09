package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.DemandParameters
import dev.breischl.keneth.core.messages.StorageParameters
import dev.breischl.keneth.core.messages.SupplyParameters

/**
 * Parameters to publish to a peer during an energy transfer.
 *
 * Each non-null field is sent as a separate EP message on every tick.
 *
 * **Important:** EP spec section 5.1 requires that every message type relevant to a device
 * (supply, demand, storage) be sent continuously at the tick rate — not only when values change.
 * A field that is non-null on the first tick must remain non-null for the lifetime of the transfer.
 * Dropping a field mid-transfer causes the remote peer to stop receiving that message type and will
 * eventually result in the remote dropping the connection due to receive timeout.
 *
 * @property supply Supply parameters to publish, or null if this device has no supply role.
 * @property demand Demand parameters to publish, or null if this device has no demand role.
 * @property storage Storage parameters to publish, or null if this device has no storage role.
 */
data class TransferParams(
    val supply: SupplyParameters? = null,
    val demand: DemandParameters? = null,
    val storage: StorageParameters? = null,
)
