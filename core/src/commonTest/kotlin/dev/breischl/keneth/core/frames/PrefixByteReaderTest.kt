package dev.breischl.keneth.core.frames

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PrefixByteReaderTest {

    private fun delegateOf(vararg bytes: Byte) = ByteArrayByteReader(byteArrayOf(*bytes))

    @Test
    fun `read single byte from prefix`() {
        val reader = PrefixByteReader(byteArrayOf(0x42), delegateOf(0x10))
        assertEquals(0x42.toByte(), reader.read())
    }

    @Test
    fun `read single byte from delegate after prefix exhausted`() {
        val reader = PrefixByteReader(byteArrayOf(0x42), delegateOf(0x10))
        reader.read() // consume prefix
        assertEquals(0x10.toByte(), reader.read())
    }

    @Test
    fun `read single byte with empty prefix delegates immediately`() {
        val reader = PrefixByteReader(byteArrayOf(), delegateOf(0x42))
        assertEquals(0x42.toByte(), reader.read())
    }

    @Test
    fun `read count from prefix only`() {
        val reader = PrefixByteReader(byteArrayOf(0x01, 0x02, 0x03), delegateOf(0x04))
        assertContentEquals(byteArrayOf(0x01, 0x02), reader.read(2))
    }

    @Test
    fun `read count spanning prefix and delegate`() {
        val reader = PrefixByteReader(byteArrayOf(0x01, 0x02), delegateOf(0x03, 0x04))
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03), reader.read(3))
    }

    @Test
    fun `read count from delegate only when prefix exhausted`() {
        val reader = PrefixByteReader(byteArrayOf(0x01), delegateOf(0x02, 0x03))
        reader.read() // exhaust prefix
        assertContentEquals(byteArrayOf(0x02, 0x03), reader.read(2))
    }

    @Test
    fun `readString spanning prefix and delegate`() {
        val prefixBytes = "he".encodeToByteArray()
        val delegateBytes = "llo".encodeToByteArray()
        val reader = PrefixByteReader(prefixBytes, ByteArrayByteReader(delegateBytes))
        assertEquals("hello", reader.readString(5))
    }

    @Test
    fun `skip within prefix only`() {
        val reader = PrefixByteReader(byteArrayOf(0x01, 0x02, 0x03), delegateOf(0x04))
        reader.skip(2)
        assertEquals(0x03.toByte(), reader.read())
    }

    @Test
    fun `skip spanning prefix and delegate`() {
        val reader = PrefixByteReader(byteArrayOf(0x01, 0x02), delegateOf(0x03, 0x04))
        reader.skip(3)
        assertEquals(0x04.toByte(), reader.read())
    }

    @Test
    fun `skip from delegate only when prefix exhausted`() {
        val reader = PrefixByteReader(byteArrayOf(0x01), delegateOf(0x02, 0x03, 0x04))
        reader.read() // exhaust prefix
        reader.skip(2)
        assertEquals(0x04.toByte(), reader.read())
    }

    @Test
    fun `skip exact prefix length transitions to delegate`() {
        val reader = PrefixByteReader(byteArrayOf(0x01, 0x02), delegateOf(0x03))
        reader.skip(2)
        assertEquals(0x03.toByte(), reader.read())
    }

    @Test
    fun `totalRead is zero before any reads`() {
        val reader = PrefixByteReader(byteArrayOf(0x01, 0x02), delegateOf(0x03))
        assertEquals(0L, reader.totalRead())
    }

    @Test
    fun `totalRead counts prefix and delegate positions`() {
        val reader = PrefixByteReader(byteArrayOf(0x01, 0x02), delegateOf(0x03, 0x04))
        reader.read()
        assertEquals(1L, reader.totalRead())
        reader.read()
        assertEquals(2L, reader.totalRead())
        reader.read()
        assertEquals(3L, reader.totalRead())
    }

    @Test
    fun `sequential reads across full prefix and delegate return all bytes in order`() {
        val reader = PrefixByteReader(byteArrayOf(0x0A, 0x0B), delegateOf(0x0C, 0x0D))
        assertEquals(0x0A.toByte(), reader.read())
        assertEquals(0x0B.toByte(), reader.read())
        assertEquals(0x0C.toByte(), reader.read())
        assertEquals(0x0D.toByte(), reader.read())
    }
}
