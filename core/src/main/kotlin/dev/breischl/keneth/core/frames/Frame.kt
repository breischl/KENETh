package dev.breischl.keneth.core.frames

/**
 * Represents an EnergyNet Protocol frame.
 *
 * A frame is the basic unit of communication in the EnergyNet Protocol.
 * On the wire it is encoded as a CBOR array of 3 elements:
 * ```
 * 9A 00 00 00 03              CBOR array(3) with 4-byte length (magic/sync marker)
 * F6 or {CBOR map}            Element 1: headers (CBOR null or map with UInt keys)
 * 1A XX XX XX XX              Element 2: message type (CBOR uint32, always 4-byte form)
 * 58/59/5A {len} {data}       Element 3: payload (CBOR bytestring)
 * ```
 *
 * @property headers Optional headers as a map from header ID to value. Empty map if no headers.
 *   Supported value types: String, Long, Int, UInt, ULong, Boolean, ByteArray, Double, Float.
 * @property messageTypeId The message type identifier (e.g., 0xFFFFFFFF for Ping).
 * @property payload The CBOR-encoded message payload.
 */
data class Frame(
    val headers: Map<UInt, Any>,
    val messageTypeId: UInt,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        if (!headersEqual(headers, other.headers)) return false
        if (messageTypeId != other.messageTypeId) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = headersHashCode(headers)
        result = 31 * result + messageTypeId.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "Frame(headers=$headers, messageTypeId=${messageTypeId.toHexString()}, payload=${payload.size} bytes)"
    }
}

private fun headersEqual(a: Map<UInt, Any>, b: Map<UInt, Any>): Boolean {
    if (a.size != b.size) return false
    for ((key, aVal) in a) {
        val bVal = b[key] ?: return false
        val eq = if (aVal is ByteArray && bVal is ByteArray) aVal.contentEquals(bVal) else aVal == bVal
        if (!eq) return false
    }
    return true
}

private fun headersHashCode(headers: Map<UInt, Any>): Int {
    var h = 0
    for ((key, value) in headers) {
        val valueHash = if (value is ByteArray) value.contentHashCode() else value.hashCode()
        h += key.hashCode() xor valueHash
    }
    return h
}
