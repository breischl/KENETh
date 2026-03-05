package dev.breischl.keneth.core.frames

import net.orandja.obor.io.ByteReader
import net.orandja.obor.io.ReaderException

/** A [ByteReader] backed by a [ByteArray]. */
internal class ByteArrayByteReader(
    private val bytes: ByteArray,
    private val maxBytes: Long = 1L shl 20,
) : ByteReader {
    private var position = 0L

    override fun totalRead(): Long = position

    override fun read(): Byte {
        if (position >= maxBytes) throw ReaderException("CBOR payload too large at position $position")
        if (position >= bytes.size) throw ReaderException("Unexpected end of bytes at position $position")
        return bytes[(position++).toInt()]
    }

    override fun read(count: Int): ByteArray {
        if (position + count > maxBytes) throw ReaderException("CBOR payload too large at position $position")
        if (position + count > bytes.size) throw ReaderException("Unexpected end of bytes at position $position")
        return bytes.copyOfRange(position.toInt(), (position + count).toInt()).also { position += count }
    }

    override fun readString(count: Int): String = read(count).decodeToString()

    override fun skip(count: Int) {
        if (position + count > maxBytes) throw ReaderException("CBOR payload too large at position $position")
        if (position + count > bytes.size) throw ReaderException("Unexpected end of bytes at position $position")
        position += count
    }
}
