package dev.breischl.keneth.core.frames

import kotlinx.io.EOFException
import kotlinx.io.Source
import net.orandja.obor.io.ByteReader
import net.orandja.obor.io.ReaderException

/**
 * A [ByteReader] backed by a [kotlinx.io.Source].
 *
 * @param source The underlying source to read from.
 * @param maxBytes Maximum number of bytes this reader will consume. Any attempt to
 *   read or allocate beyond this limit throws [ReaderException]. This prevents
 *   corrupted CBOR length fields from causing [OutOfMemoryError] by requesting
 *   enormous allocations. Defaults to [DEFAULT_MAX_BYTES].
 */
internal class SourceByteReader(
    private val source: Source,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : ByteReader {
    private var position = 0L

    override fun totalRead(): Long = position

    override fun read(): Byte {
        checkLimit(1)
        try {
            val b = source.readByte()
            position++
            return b
        } catch (e: EOFException) {
            throw ReaderException("Unexpected end of source at position $position", e)
        }
    }

    override fun read(count: Int): ByteArray {
        checkLimit(count.toLong())
        val result = ByteArray(count)
        var offset = 0
        try {
            while (offset < count) {
                val n = source.readAtMostTo(result, offset, count)
                if (n == -1) throw ReaderException("Unexpected end of source at position $position")
                offset += n
                position += n
            }
        } catch (e: EOFException) {
            throw ReaderException("Unexpected end of source at position $position", e)
        }
        return result
    }

    override fun readString(count: Int): String = read(count).decodeToString()

    override fun skip(count: Int) {
        checkLimit(count.toLong())
        try {
            source.skip(count.toLong())
            position += count
        } catch (e: EOFException) {
            throw ReaderException("Unexpected end of source at position $position", e)
        }
    }

    private fun checkLimit(requested: Long) {
        if (position + requested > maxBytes) {
            throw ReaderException(
                "Requested $requested bytes at position $position exceeds limit of $maxBytes bytes"
            )
        }
    }

    companion object {
        /** Default maximum frame size: 1 MiB. */
        const val DEFAULT_MAX_BYTES: Long = 1L shl 20
    }
}
