package dev.breischl.keneth.core.parsing

import dev.breischl.keneth.core.diagnostics.DiagnosticCollector
import dev.breischl.keneth.core.diagnostics.DiagnosticContext
import dev.breischl.keneth.core.messages.*
import net.orandja.obor.codec.Cbor

/**
 * Function type for message parsers.
 *
 * Takes the CBOR payload bytes and a diagnostic collector, returns the
 * parsed message or null if parsing failed.
 */
typealias MessageParserFunc = (ByteArray, DiagnosticCollector) -> Message?

/**
 * Registry of known message types and their parsers.
 *
 * This object provides a mapping from message type IDs to their corresponding
 * parser functions. It is used by [LenientMessageParser] and [StrictMessageParser]
 * to decode message payloads.
 */
object MessageRegistry {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    private val messageTypes = mapOf(
        Ping.typeId to Ping.payloadSerializer,
        SessionParameters.TYPE_ID to SessionParameters.serializer(),
        DemandParameters.TYPE_ID to DemandParameters.serializer(),
        SoftDisconnect.TYPE_ID to SoftDisconnect.serializer(),
        StorageParameters.TYPE_ID to StorageParameters.serializer(),
        SupplyParameters.TYPE_ID to SupplyParameters.serializer()
    )

    /**
     * Returns the parser function for the given message type ID.
     *
     * @param typeId The message type ID.
     * @return The parser function, or null if the type is unknown.
     */
    fun parserFor(typeId: UInt): MessageParserFunc? {
        val serializer = messageTypes[typeId] ?: return null

        return { bytes, collector ->
            try {
                DiagnosticContext.withCollector(collector) {
                    cbor.decodeFromByteArray(serializer, bytes)
                }
            } catch (e: Exception) {
                collector.error("PARSE_ERROR", "Failed to parse message type $typeId: ${e.message}")
                null
            }
        }
    }

    /**
     * Returns true if the given message type ID is recognized.
     *
     * @param typeId The message type ID to check.
     * @return True if a parser is registered for this type.
     */
    fun isKnownType(typeId: UInt): Boolean = typeId in messageTypes
}
