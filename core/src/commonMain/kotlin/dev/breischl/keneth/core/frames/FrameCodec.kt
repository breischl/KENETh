package dev.breischl.keneth.core.frames

import dev.breischl.keneth.core.diagnostics.*
import dev.breischl.keneth.core.parsing.ParseResult
import kotlinx.io.Source
import net.orandja.obor.codec.Cbor
import net.orandja.obor.data.*
import net.orandja.obor.io.ByteReader
import net.orandja.obor.io.CborReader
import net.orandja.obor.io.ReaderException

/**
 * Codec for encoding and decoding EnergyNet Protocol frames.
 *
 * The frame is a CBOR array of 3 elements with a non-canonical 4-byte length header
 * that serves as a magic/sync marker:
 * ```
 * 9A 00 00 00 03              CBOR array(3) with 4-byte length (magic/sync marker)
 * F6 or {CBOR map}            Element 1: headers (CBOR null or map with UInt keys)
 * 1A XX XX XX XX              Element 2: message type (CBOR uint32, always 4-byte form)
 * 58/59/5A {len} {data}       Element 3: payload (CBOR bytestring)
 * ```
 *
 * Example usage:
 * ```kotlin
 * // Encoding
 * val frame = Frame(emptyMap(), 0xFFFFFFFFu, byteArrayOf())
 * val bytes = FrameCodec.encode(frame)
 *
 * // Decoding
 * val result = FrameCodec.decode(bytes)
 * if (result.succeeded) {
 *     val decoded = result.value!!
 * }
 * ```
 */
object FrameCodec {
    /** Magic bytes identifying the EnergyNet Protocol (CBOR array(3) with 4-byte length). */
    val MAGIC_BYTES = byteArrayOf(0x9A.toByte(), 0x00, 0x00, 0x00, 0x03)

    // "ingnoreUnknownKeys" is a typo in the OBOR library API (should be "ignore")
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    /**
     * Encodes a frame to its wire format representation.
     *
     * @param frame The frame to encode.
     * @return The encoded byte array.
     */
    fun encode(frame: Frame): ByteArray {
        // 1. Magic bytes (non-canonical CBOR array(3) header for sync recovery)
        var result = MAGIC_BYTES

        // 2. Headers: CBOR null if empty, CBOR map otherwise
        result += if (frame.headers.isEmpty()) {
            cbor.encodeToByteArray(CborObject.serializer(), CborNull)
        } else {
            val headerMap = CborObject.buildMap {
                for ((key, value) in frame.headers) {
                    put(CborObject.positive(key.toInt()), toCborObject(value))
                }
            }
            cbor.encodeToByteArray(CborMap.serializer(), headerMap)
        }

        // 3. Message type: CBOR uint32 with forced 1A prefix (spec requires 4-byte encoding)
        val typeId = frame.messageTypeId
        result += byteArrayOf(
            0x1A.toByte(),
            (typeId shr 24).toByte(),
            (typeId shr 16).toByte(),
            (typeId shr 8).toByte(),
            typeId.toByte()
        )

        // 4. Payload: CBOR bytestring
        result += cbor.encodeToByteArray(CborObject.serializer(), CborObject.value(frame.payload))

        return result
    }

    /**
     * Decodes a frame from its wire format representation.
     *
     * @param bytes The encoded frame bytes.
     * @return A ParseResult containing the decoded frame or error diagnostics.
     */
    fun decode(bytes: ByteArray): ParseResult<Frame> {
        return decodeFromReader(ByteArrayByteReader(bytes, bytes.size.toLong()), DiagnosticContext.get())
            ?: ParseResult.failure(
                listOf(
                    Diagnostic(
                        Severity.ERROR,
                        "FRAME_TOO_SHORT",
                        "Frame is empty (0 bytes)",
                        byteOffset = 0
                    )
                )
            )
    }

    /**
     * Reads and decodes a single frame from a [Source].
     *
     * Returns null on clean EOF (source exhausted), or a [ParseResult] for success or failure.
     *
     * @param source The source to read from.
     * @param maxBytes Maximum number of bytes to read for a single frame.
     * @return A ParseResult on success or failure, or null on clean EOF.
     */
    fun decodeFromSource(
        source: Source,
        maxBytes: Long = SourceByteReader.DEFAULT_MAX_BYTES,
    ): ParseResult<Frame>? {
        if (source.exhausted()) return null
        return decodeFromReader(SourceByteReader(source, maxBytes), DiagnosticContext.get())
    }

    /**
     * Reads and decodes a single frame from a [ByteReader].
     *
     * Returns null on clean EOF (no data available), or a [ParseResult] for success or failure.
     *
     * @param reader The byte reader to read from.
     * @param collector Optional diagnostic collector for warnings/errors found during parsing.
     * @return A ParseResult on success or failure, or null on clean EOF.
     */
    internal fun decodeFromReader(reader: ByteReader, collector: DiagnosticCollector?): ParseResult<Frame>? {
        val myCollector = collector ?: DiagnosticCollector()

        // Read first byte — if reader is empty this is a clean EOF
        val firstByte: Byte
        try {
            firstByte = reader.read()
        } catch (_: ReaderException) {
            return null
        } catch (_: Exception) {
            return null
        }

        // Validate the array header (magic bytes).
        // The spec requires non-canonical 4-byte length (9A 00 00 00 03), but we also
        // accept canonical CBOR array(3) (83) with a warning.
        val headerBytes: ByteArray = when (firstByte) {
            0x9A.toByte() -> {
                // Non-canonical 4-byte length (spec-required): read remaining 4 bytes
                val rest = try {
                    reader.read(4)
                } catch (_: Exception) {
                    return streamError("READ_ERROR", "Incomplete magic bytes")
                }
                if (rest[0] != 0x00.toByte() || rest[1] != 0x00.toByte() ||
                    rest[2] != 0x00.toByte() || rest[3] != 0x03.toByte()
                ) {
                    myCollector.warning(
                        "INVALID_ARRAY_LENGTH",
                        "Non-standard array length after 9A header, attempting to parse"
                    )
                }
                byteArrayOf(0x9A.toByte()) + rest
            }

            0x83.toByte() -> {
                // Canonical CBOR array(3) — valid but not spec-compliant
                myCollector.warning(
                    "CANONICAL_ARRAY_HEADER",
                    "Frame uses canonical CBOR array header (83) instead of " +
                            "spec-required non-canonical form (9A 00 00 00 03)"
                )
                byteArrayOf(0x83.toByte())
            }

            else -> {
                return streamError(
                    "INVALID_MAGIC",
                    "Expected CBOR array header, got 0x${
                        firstByte.toUByte().toString(16).padStart(2, '0')
                    }"
                )
            }
        }

        // Feed validated header bytes + rest of reader to OBOR.
        // PrefixByteReader serves the already-consumed header bytes first, then delegates.
        val cborByteReader = PrefixByteReader(headerBytes, reader)
        val cborReader = CborReader.ByReader(cborByteReader)
        val parsed: CborObject
        try {
            parsed = cbor.decodeFromReader(CborObject.serializer(), cborReader)
        } catch (e: ReaderException) {
            myCollector.error("READ_ERROR", "Incomplete frame: ${e.message}")
            return ParseResult.failure(myCollector.diagnostics)
        } catch (e: Exception) {
            myCollector.error("INVALID_FRAME", "Failed to decode frame: ${e.message}")
            return ParseResult.failure(myCollector.diagnostics)
        }

        if (parsed !is CborArray) {
            myCollector.error(
                "INVALID_FRAME",
                "Expected CBOR array, got ${parsed::class.simpleName}"
            )
            return ParseResult.failure(myCollector.diagnostics)
        }

        val frame = extractFrame(parsed, myCollector)
        return if (frame != null) {
            ParseResult.success(frame, myCollector.diagnostics)
        } else {
            ParseResult.failure(myCollector.diagnostics)
        }
    }

    private fun toCborObject(value: Any): CborObject = when (value) {
        is String -> CborObject.value(value)
        is Boolean -> CborObject.value(value)
        is ByteArray -> CborObject.value(value)
        is Int -> CborObject.value(value)
        is Long -> CborObject.value(value)
        is UInt -> CborObject.positive(value)
        is ULong -> CborObject.positive(value)
        is Float -> CborObject.value(value)
        is Double -> CborObject.value(value)
        else -> throw IllegalArgumentException(
            "Unsupported header value type: ${value::class.simpleName}. " +
                    "Supported types: String, Boolean, ByteArray, Int, Long, UInt, ULong, Float, Double"
        )
    }

    private fun toKotlinValue(obj: CborObject): Any? = when (obj) {
        is CborText -> obj.value
        is CborPositive -> obj.value.toLong()
        is CborNegative -> -1L - obj.value.toLong()
        is CborBoolean -> obj.value
        is CborBytes -> obj.value
        is CborFloat -> obj.value
        is CborNull -> null
        else -> null
    }

    internal fun streamError(code: String, message: String): ParseResult<Frame> =
        ParseResult.failure(listOf(Diagnostic(Severity.ERROR, code, message)))

    /**
     * Extracts a [Frame] from a decoded CBOR array, validating element types and count.
     *
     * @param array The decoded CBOR array (expected to have 3 elements).
     * @param collector Collector for diagnostics generated during extraction.
     * @return The extracted frame, or null if extraction failed.
     */
    private fun extractFrame(array: CborArray, collector: DiagnosticCollector): Frame? {
        if (array.size != 3) {
            collector.error(
                "INVALID_FRAME",
                "Expected 3 elements in frame array, got ${array.size}",
                byteOffset = MAGIC_BYTES.size
            )
            return null
        }

        // Element 0: headers (CborNull or CborMap)
        val headers: Map<UInt, Any> = when (val h = array[0]) {
            is CborNull -> emptyMap()
            is CborMap -> {
                val result = mutableMapOf<UInt, Any>()
                for ((k, v) in h.asMap) {
                    if (k !is CborPositive) {
                        collector.warning(
                            "INVALID_HEADER_KEY",
                            "Non-integer header key ignored: ${k::class.simpleName}"
                        )
                        continue
                    }
                    val converted = toKotlinValue(v)
                    if (converted == null) {
                        collector.warning(
                            "UNSUPPORTED_HEADER_VALUE",
                            "Unsupported CBOR header value type ignored: ${v::class.simpleName}"
                        )
                        continue
                    }
                    result[k.value.toUInt()] = converted
                }
                result
            }

            else -> {
                collector.warning(
                    "INVALID_HEADERS",
                    "Expected null or map for headers, got ${h::class.simpleName}",
                    byteOffset = MAGIC_BYTES.size
                )
                emptyMap()
            }
        }

        // Element 1: message type (CborPositive)
        val messageTypeId = when (val t = array[1]) {
            is CborPositive -> t.value.toUInt()
            else -> {
                collector.error(
                    "INVALID_TYPE",
                    "Expected unsigned integer for message type, got ${t::class.simpleName}",
                    byteOffset = MAGIC_BYTES.size
                )
                return null
            }
        }

        // Element 2: payload (CborBytes)
        val payload = when (val p = array[2]) {
            is CborBytes -> p.value
            else -> {
                collector.error(
                    "INVALID_PAYLOAD",
                    "Expected bytestring for payload, got ${p::class.simpleName}",
                    byteOffset = MAGIC_BYTES.size
                )
                return null
            }
        }

        return Frame(headers, messageTypeId, payload)
    }
}
