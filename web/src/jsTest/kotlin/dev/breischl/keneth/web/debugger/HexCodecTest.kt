package dev.breischl.keneth.web.debugger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexCodecTest {

    @Test
    fun decode_validUppercaseHex_returnsBytes() {
        val result = HexCodec.decode("48656C6C6F")
        assertTrue(result.isSuccess)
        assertEquals("Hello".encodeToByteArray().toList(), result.getOrThrow().toList())
    }

    @Test
    fun decode_validLowercaseHex_returnsBytes() {
        val result = HexCodec.decode("48656c6c6f")
        assertTrue(result.isSuccess)
        assertEquals("Hello".encodeToByteArray().toList(), result.getOrThrow().toList())
    }

    @Test
    fun decode_withWhitespace_stripsAndDecodes() {
        val result = HexCodec.decode("48 65 6C 6C 6F")
        assertTrue(result.isSuccess)
        assertEquals("Hello".encodeToByteArray().toList(), result.getOrThrow().toList())
    }

    @Test
    fun decode_withNewlinesAndTabs_stripsAndDecodes() {
        val result = HexCodec.decode("48\n65\t6C\r6C 6F")
        assertTrue(result.isSuccess)
        assertEquals("Hello".encodeToByteArray().toList(), result.getOrThrow().toList())
    }

    @Test
    fun decode_with0xPrefix_stripsAndDecodes() {
        val result = HexCodec.decode("0x48656C6C6F")
        assertTrue(result.isSuccess)
        assertEquals("Hello".encodeToByteArray().toList(), result.getOrThrow().toList())
    }

    @Test
    fun decode_emptyString_returnsEmptyBytes() {
        val result = HexCodec.decode("")
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow().toList())
    }

    @Test
    fun decode_onlyWhitespace_returnsEmptyBytes() {
        val result = HexCodec.decode("  \t\n  ")
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow().toList())
    }

    @Test
    fun decode_oddLengthHex_returnsFailure() {
        val result = HexCodec.decode("ABC")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("odd"))
    }

    @Test
    fun decode_invalidCharacters_returnsFailure() {
        val result = HexCodec.decode("ZZZZ")
        assertTrue(result.isFailure)
    }

    @Test
    fun encode_bytesToUppercaseHex() {
        val hex = HexCodec.encode("Hello".encodeToByteArray())
        assertEquals("48656C6C6F", hex)
    }

    @Test
    fun encode_emptyBytes_returnsEmptyString() {
        val hex = HexCodec.encode(byteArrayOf())
        assertEquals("", hex)
    }

    @Test
    fun roundtrip_encodeThenDecode_returnsOriginalBytes() {
        val original = byteArrayOf(0x9A.toByte(), 0x00, 0x00, 0x00, 0x03, 0xF6.toByte())
        val hex = HexCodec.encode(original)
        val decoded = HexCodec.decode(hex).getOrThrow()
        assertEquals(original.toList(), decoded.toList())
    }
}
