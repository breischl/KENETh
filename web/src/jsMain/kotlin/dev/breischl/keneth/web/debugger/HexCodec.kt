package dev.breischl.keneth.web.debugger

/**
 * Utility for converting between hex strings and byte arrays.
 *
 * Handles user-facing input concerns: tolerates whitespace, mixed case,
 * and an optional `0x` prefix. Produces uppercase hex output.
 */
object HexCodec {

    /**
     * Decodes a hex string to a byte array.
     *
     * Strips whitespace, removes an optional `0x` or `0X` prefix,
     * and validates that the remaining characters are valid hex digits
     * with an even length.
     *
     * @param hex The hex string to decode.
     * @return A [Result] containing the decoded bytes, or a failure with a descriptive message.
     */
    fun decode(hex: String): Result<ByteArray> {
        val cleaned = hex.replace(Regex("\\s"), "")
            .removePrefix("0x")
            .removePrefix("0X")

        if (cleaned.isEmpty()) return Result.success(byteArrayOf())

        if (cleaned.length % 2 != 0) {
            return Result.failure(
                IllegalArgumentException("Hex string has odd length (${cleaned.length}): must have an even number of hex digits")
            )
        }

        return try {
            Result.success(cleaned.hexToByteArray())
        } catch (e: IllegalArgumentException) {
            Result.failure(IllegalArgumentException("Invalid hex characters in input: ${e.message}"))
        }
    }

    /**
     * Encodes a byte array to an uppercase hex string with no separators.
     *
     * @param bytes The bytes to encode.
     * @return The uppercase hex string.
     */
    fun encode(bytes: ByteArray): String {
        return bytes.toHexString(HexFormat.UpperCase)
    }
}
