package dev.breischl.keneth.core.frames

import net.orandja.obor.io.ReaderException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteArrayByteReaderTest {

    @Test
    fun `read single byte returns correct value`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x42, 0x10))
        assertEquals(0x42.toByte(), reader.read())
    }

    @Test
    fun `read single byte advances position`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02, 0x03))
        reader.read()
        assertEquals(1L, reader.totalRead())
        reader.read()
        assertEquals(2L, reader.totalRead())
    }

    @Test
    fun `read sequential bytes returns correct values`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02, 0x03))
        assertEquals(0x01.toByte(), reader.read())
        assertEquals(0x02.toByte(), reader.read())
        assertEquals(0x03.toByte(), reader.read())
    }

    @Test
    fun `read count returns correct slice`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        assertContentEquals(byteArrayOf(0x01, 0x02), reader.read(2))
    }

    @Test
    fun `read count advances position by count`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        reader.read(3)
        assertEquals(3L, reader.totalRead())
    }

    @Test
    fun `readString decodes bytes as UTF-8`() {
        val bytes = "hello".encodeToByteArray()
        val reader = ByteArrayByteReader(bytes)
        assertEquals("hello", reader.readString(bytes.size))
    }

    @Test
    fun `skip advances position without returning bytes`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        reader.skip(2)
        assertEquals(2L, reader.totalRead())
        assertEquals(0x03.toByte(), reader.read())
    }

    @Test
    fun `totalRead is zero before any reads`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02))
        assertEquals(0L, reader.totalRead())
    }

    @Test
    fun `read past end throws ReaderException`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01))
        reader.read()
        assertFailsWith<ReaderException> { reader.read() }
    }

    @Test
    fun `read count past end throws ReaderException`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02))
        assertFailsWith<ReaderException> { reader.read(3) }
    }

    @Test
    fun `skip past end throws ReaderException`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02))
        assertFailsWith<ReaderException> { reader.skip(3) }
    }

    @Test
    fun `read single byte past maxBytes limit throws ReaderException`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02, 0x03), maxBytes = 2L)
        reader.read()
        reader.read()
        assertFailsWith<ReaderException> { reader.read() }
    }

    @Test
    fun `read count past maxBytes limit throws ReaderException`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02, 0x03), maxBytes = 2L)
        assertFailsWith<ReaderException> { reader.read(3) }
    }

    @Test
    fun `skip past maxBytes limit throws ReaderException`() {
        val reader = ByteArrayByteReader(byteArrayOf(0x01, 0x02, 0x03), maxBytes = 2L)
        assertFailsWith<ReaderException> { reader.skip(3) }
    }
}
