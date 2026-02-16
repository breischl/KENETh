package dev.breischl.keneth.core.messages

import kotlinx.serialization.KSerializer

/**
 * Base class for all EnergyNet Protocol messages.
 *
 * Messages are the primary means of communication in the EnergyNet Protocol.
 * Each message type has a unique type ID and carries specific information
 * about the charging session, equipment capabilities, or control commands.
 *
 * @property typeId The unique identifier for this message type.
 */
sealed class Message {
    abstract val typeId: UInt
    abstract val payloadSerializer: KSerializer<out Message>
}
