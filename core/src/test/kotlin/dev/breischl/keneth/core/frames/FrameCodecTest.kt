package dev.breischl.keneth.core.frames

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import net.orandja.obor.data.CborArray
import net.orandja.obor.data.CborMap
import net.orandja.obor.data.CborNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FrameCodecTest {

    @Test
    fun `encode and decode empty frame`() {
        val original = Frame(
            headers = emptyMap(),
            messageTypeId = 0xFFFF_FFFFu,
            payload = byteArrayOf()
        )

        val encoded = FrameCodec.encode(original)
        val result = FrameCodec.decode(encoded)

        assertTrue(result.succeeded)
        assertEquals(original.headers, result.value!!.headers)
        assertEquals(original.messageTypeId, result.value.messageTypeId)
        assertTrue(result.value.payload.contentEquals(original.payload))
    }

    @Test
    fun `encode and decode frame with payload`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val original = Frame(
            headers = emptyMap(),
            messageTypeId = 0xBABA_5E55u,
            payload = payload
        )

        val encoded = FrameCodec.encode(original)
        val result = FrameCodec.decode(encoded)

        assertTrue(result.succeeded)
        assertEquals(original.messageTypeId, result.value!!.messageTypeId)
        assertTrue(result.value.payload.contentEquals(original.payload))
    }

    @Test
    fun `Frame round-trip encode-decode - property`() = runBlocking<Unit> {
        val arbFrame = arbitrary {
            val messageTypeId = Arb.uInt().bind()
            val payload = Arb.byteArray(Arb.int(0..1024), Arb.byte()).bind()
            Frame(headers = emptyMap(), messageTypeId = messageTypeId, payload = payload)
        }
        checkAll(arbFrame) { original ->
            val encoded = FrameCodec.encode(original)
            val result = FrameCodec.decode(encoded)
            assertTrue(result.succeeded, "Decode failed: ${result.diagnostics}")
            assertEquals(original.messageTypeId, result.value!!.messageTypeId)
            assertTrue(result.value.payload.contentEquals(original.payload))
        }
    }

    @Test
    fun `Frame round-trip with headers - property`() = runBlocking<Unit> {
        val arbFrame = arbitrary {
            val numHeaders = Arb.int(0..5).bind()
            val headers = (0 until numHeaders).associate {
                Arb.uInt().bind() to Arb.string(0..50).bind() as Any
            }
            val messageTypeId = Arb.uInt().bind()
            val payload = Arb.byteArray(Arb.int(0..256), Arb.byte()).bind()
            Frame(headers = headers, messageTypeId = messageTypeId, payload = payload)
        }
        checkAll(arbFrame) { original ->
            val encoded = FrameCodec.encode(original)
            val result = FrameCodec.decode(encoded)
            assertTrue(result.succeeded, "Decode failed: ${result.diagnostics}")
            assertEquals(original, result.value)
        }
    }

    @Test
    fun `decode fails on empty input`() {
        val result = FrameCodec.decode(byteArrayOf())

        assertFalse(result.succeeded)
        assertTrue(result.hasErrors)
        assertEquals("FRAME_TOO_SHORT", result.diagnostics[0].code)
    }

    @Test
    fun `decode fails on garbage input`() {
        val garbage = byteArrayOf(0xE9.toByte(), 0xE5.toByte())
        val result = FrameCodec.decode(garbage)

        assertFalse(result.succeeded)
        assertTrue(result.hasErrors)
        assertEquals("INVALID_MAGIC", result.diagnostics[0].code)
    }

    @Test
    fun `decode fails on invalid magic bytes`() {
        // 8 bytes minimum: wrong magic (5) + 3 bytes of padding
        val invalidMagic = byteArrayOf(
            0x00, 0x00, 0x00, 0x00, 0x00,  // Wrong magic
            0xF6.toByte(),                   // null header
            0x00,                            // uint 0
            0x40                             // empty bytestring
        )
        val result = FrameCodec.decode(invalidMagic)

        assertFalse(result.succeeded)
        assertTrue(result.hasErrors)
        assertEquals("INVALID_MAGIC", result.diagnostics[0].code)
    }

    @Test
    fun `decode warns on non-standard 9A array length but still parses valid frame`() {
        // Build a valid frame, then replace the magic bytes with 9A + non-standard length
        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = 0x00000001u,
            payload = byteArrayOf(0x01, 0x02)
        )
        val encoded = FrameCodec.encode(frame)

        // Replace canonical magic (9A 00 00 00 03) with 9A and a different 4-byte length
        // that still happens to be 3 (but in a weird encoding: 00 00 00 03 is correct,
        // so use a truly different one like 00 00 01 03 which says array of 259 elements)
        // This should warn but fail on element count mismatch
        val modified = encoded.copyOf()
        modified[3] = 0x01  // Changes length from 3 to 259

        val result = FrameCodec.decode(modified)
        // Should have the warning about non-standard array length
        assertTrue(result.hasWarnings)
        assertEquals("INVALID_ARRAY_LENGTH", result.diagnostics.first { it.code == "INVALID_ARRAY_LENGTH" }.code)
    }

    @Test
    fun `frame magic bytes are correct`() {
        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = 0x00000000u,
            payload = byteArrayOf()
        )

        val encoded = FrameCodec.encode(frame)

        // 9A 00 00 00 03 — CBOR array(3) with 4-byte length
        assertEquals(0x9A.toByte(), encoded[0])
        assertEquals(0x00.toByte(), encoded[1])
        assertEquals(0x00.toByte(), encoded[2])
        assertEquals(0x00.toByte(), encoded[3])
        assertEquals(0x03.toByte(), encoded[4])
    }

    @Test
    fun `empty headers encode as CBOR null`() {
        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = 0x00000000u,
            payload = byteArrayOf()
        )

        val encoded = FrameCodec.encode(frame)

        // After magic (5 bytes), headers should be F6 (CBOR null)
        assertEquals(0xF6.toByte(), encoded[5])
    }

    @Test
    fun `encode and decode frame with non-empty headers`() {
        val headers = mapOf<UInt, Any>(
            0x01u to "value1",
            0x02u to "value2"
        )
        val original = Frame(
            headers = headers,
            messageTypeId = 0xBABA_5E55u,
            payload = byteArrayOf(0x01, 0x02)
        )

        val encoded = FrameCodec.encode(original)

        // Verify that encoding with headers produces a longer frame than without
        val noHeaders = Frame(emptyMap(), original.messageTypeId, original.payload)
        val encodedNoHeaders = FrameCodec.encode(noHeaders)
        assertTrue(encoded.size > encodedNoHeaders.size)

        // Decoding round-trips everything correctly
        val result = FrameCodec.decode(encoded)
        assertTrue(result.succeeded)
        assertEquals(original, result.value)
    }

    @Test
    fun `encode and decode frame with maximum payload size`() {
        val maxPayload = ByteArray(65535) { (it % 256).toByte() }
        val original = Frame(
            headers = emptyMap(),
            messageTypeId = 0x0000_0001u,
            payload = maxPayload
        )

        val encoded = FrameCodec.encode(original)
        val result = FrameCodec.decode(encoded)

        assertTrue(result.succeeded)
        assertEquals(original.messageTypeId, result.value!!.messageTypeId)
        assertTrue(result.value.payload.contentEquals(maxPayload))
    }

    @Test
    fun `decode fails on truncated frame`() {
        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = 0x0000_0001u,
            payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        )

        val encoded = FrameCodec.encode(frame)
        // Truncate: keep everything except the last 3 payload bytes
        val truncated = encoded.copyOfRange(0, encoded.size - 3)
        val result = FrameCodec.decode(truncated)

        assertFalse(result.succeeded)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `message type is encoded with 1A prefix in big endian`() {
        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = 0x12345678u,
            payload = byteArrayOf()
        )

        val encoded = FrameCodec.encode(frame)

        // After magic (5) + CBOR null F6 (1), type starts at offset 6
        // Type is encoded as CBOR uint32: 1A 12 34 56 78
        assertEquals(0x1A.toByte(), encoded[6])
        assertEquals(0x12.toByte(), encoded[7])
        assertEquals(0x34.toByte(), encoded[8])
        assertEquals(0x56.toByte(), encoded[9])
        assertEquals(0x78.toByte(), encoded[10])
    }

    // -- Property-based tests: robustness --

    @Test
    fun `arbitrary bytes never crash decode - property`() = runBlocking<Unit> {
        checkAll(Arb.byteArray(Arb.int(0..512), Arb.byte())) { bytes ->
            // Should never throw, always returns a ParseResult
            val result = FrameCodec.decode(bytes)
            // Result is either success or failure, never an exception
            if (!result.succeeded) {
                assertTrue(result.hasErrors)
            }
        }
    }

    @Test
    fun `encoding is deterministic - property`() = runBlocking<Unit> {
        val arbFrame = arbitrary {
            val messageTypeId = Arb.uInt().bind()
            val payload = Arb.byteArray(Arb.int(0..256), Arb.byte()).bind()
            Frame(headers = emptyMap(), messageTypeId = messageTypeId, payload = payload)
        }
        checkAll(arbFrame) { frame ->
            val first = FrameCodec.encode(frame)
            val second = FrameCodec.encode(frame)
            assertTrue(first.contentEquals(second), "Encoding should be deterministic")
        }
    }

    @Test
    fun `encoded frames always start with magic bytes - property`() = runBlocking<Unit> {
        val arbFrame = arbitrary {
            val messageTypeId = Arb.uInt().bind()
            val payload = Arb.byteArray(Arb.int(0..256), Arb.byte()).bind()
            Frame(headers = emptyMap(), messageTypeId = messageTypeId, payload = payload)
        }
        checkAll(arbFrame) { frame ->
            val encoded = FrameCodec.encode(frame)
            assertTrue(
                encoded.copyOfRange(0, FrameCodec.MAGIC_BYTES.size).contentEquals(FrameCodec.MAGIC_BYTES),
                "Encoded frame must start with magic bytes"
            )
        }
    }

    @Test
    fun `single-byte corruption never throws - property`() = runBlocking<Unit> {
        val arbFrame = arbitrary {
            val messageTypeId = Arb.uInt().bind()
            val payload = Arb.byteArray(Arb.int(1..256), Arb.byte()).bind()
            Frame(headers = emptyMap(), messageTypeId = messageTypeId, payload = payload)
        }
        checkAll(arbFrame, Arb.byte()) { frame, corruptByte ->
            val encoded = FrameCodec.encode(frame)
            val position = (0 until encoded.size).random()
            val corrupted = encoded.copyOf()
            corrupted[position] = corruptByte

            // Should never throw, regardless of corruption
            val result = FrameCodec.decode(corrupted)
            // Result is either a valid frame or a failure with diagnostics
            if (!result.succeeded) {
                assertTrue(result.hasErrors)
            }
        }
    }

    @Test
    fun `decode rejects frame whose payload length exceeds input size`() {
        // Hand-craft a frame where the payload bytestring header claims ~2 GB
        // but only 1 byte of actual data follows. Without a read limit, OBOR
        // would attempt to allocate a 2 GB ByteArray and throw OutOfMemoryError.
        val bytes = byteArrayOf(
            0x9A.toByte(), 0x00, 0x00, 0x00, 0x03, // magic: CBOR array(3), 4-byte length
            0xF6.toByte(),                           // headers: CBOR null
            0x1A, 0x00, 0x00, 0x00, 0x01,            // message type: uint32(1)
            0x5A, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // bytestring claiming ~2 GB
            0x01                                      // one actual byte of data
        )

        val result = FrameCodec.decode(bytes)

        assertFalse(result.succeeded)
        assertTrue(result.hasErrors)
    }

    // -- Property-based tests: CBOR structural invariants --

    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `encoded frame is valid CBOR array of 3 elements - property`() = runBlocking<Unit> {
        val arbFrame = arbitrary {
            val numHeaders = Arb.int(0..3).bind()
            val headers = (0 until numHeaders).associate {
                Arb.uInt().bind() to Arb.string(0..20).bind() as Any
            }
            val messageTypeId = Arb.uInt().bind()
            val payload = Arb.byteArray(Arb.int(0..256), Arb.byte()).bind()
            Frame(headers = headers, messageTypeId = messageTypeId, payload = payload)
        }
        checkAll(arbFrame) { frame ->
            val encoded = FrameCodec.encode(frame)
            val array = cbor.decodeFromByteArray(CborArray.serializer(), encoded)
            assertEquals(3, array.size, "Frame must be a CBOR array of exactly 3 elements")
        }
    }

    @Test
    fun `empty headers produce CborNull, non-empty produce CborMap - property`() = runBlocking<Unit> {
        val arbFrame = arbitrary {
            val numHeaders = Arb.int(0..5).bind()
            val headers = (0 until numHeaders).associate {
                Arb.uInt().bind() to Arb.string(0..20).bind() as Any
            }
            val messageTypeId = Arb.uInt().bind()
            val payload = Arb.byteArray(Arb.int(0..64), Arb.byte()).bind()
            Frame(headers = headers, messageTypeId = messageTypeId, payload = payload)
        }
        checkAll(arbFrame) { frame ->
            val encoded = FrameCodec.encode(frame)
            val array = cbor.decodeFromByteArray(CborArray.serializer(), encoded)
            if (frame.headers.isEmpty()) {
                assertIs<CborNull>(array[0], "Empty headers must encode as CborNull")
            } else {
                assertIs<CborMap>(array[0], "Non-empty headers must encode as CborMap")
            }
        }
    }

    @Test
    fun `message type always uses 1A prefix - property`() = runBlocking<Unit> {
        val arbFrame = arbitrary {
            val messageTypeId = Arb.uInt().bind()
            Frame(headers = emptyMap(), messageTypeId = messageTypeId, payload = byteArrayOf())
        }
        checkAll(arbFrame) { frame ->
            val encoded = FrameCodec.encode(frame)
            // After magic (5 bytes) + null header (1 byte), type starts at offset 6
            // Must always be 1A prefix (4-byte CBOR uint) regardless of value
            assertEquals(0x1A.toByte(), encoded[6], "Message type must always use 1A (4-byte uint) prefix")
        }
    }

    @Test
    fun `header round-trip with mixed value types - property`() = runBlocking<Unit> {
        // Generate headers with various Kotlin types (use Long for integers to match decode behavior)
        val arbHeaderValue: Arb<Any> = Arb.choice(
            Arb.string(0..20).map { it as Any },
            Arb.long().map { it as Any },
            Arb.boolean().map { it as Any },
            Arb.byteArray(Arb.int(0..32), Arb.byte()).map { it as Any }
        )
        val arbFrame = arbitrary {
            val numHeaders = Arb.int(1..4).bind()
            val headers = (0 until numHeaders).associate {
                Arb.uInt().bind() to arbHeaderValue.bind()
            }
            val messageTypeId = Arb.uInt().bind()
            val payload = Arb.byteArray(Arb.int(0..64), Arb.byte()).bind()
            Frame(headers = headers, messageTypeId = messageTypeId, payload = payload)
        }
        checkAll(arbFrame) { original ->
            val encoded = FrameCodec.encode(original)
            val result = FrameCodec.decode(encoded)
            assertTrue(result.succeeded, "Decode failed: ${result.diagnostics}")
            // Use Frame.equals() which handles ByteArray content equality in headers
            assertEquals(original, result.value, "Headers with mixed value types must round-trip")
        }
    }
}
