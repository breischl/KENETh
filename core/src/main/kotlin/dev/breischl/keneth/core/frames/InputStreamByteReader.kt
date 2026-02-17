package dev.breischl.keneth.core.frames

import net.orandja.obor.io.ByteReader
import net.orandja.obor.io.ReaderException
import java.io.InputStream

/**
 * A [ByteReader] that reads from a [java.io.InputStream].
 *
 * @param inputStream The underlying stream to read from.
 * @param maxBytes Maximum number of bytes this reader will consume. Any attempt to
 *   read or allocate beyond this limit throws [ReaderException]. This prevents
 *   corrupted CBOR length fields from causing [OutOfMemoryError] by requesting
 *   enormous allocations. Defaults to [DEFAULT_MAX_BYTES].
 */
internal class InputStreamByteReader(
    private val inputStream: InputStream,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : ByteReader {
    private var position = 0L

    override fun totalRead(): Long = position

    override fun read(): Byte {
        if (position >= maxBytes) {
            throw ReaderException("Read limit exceeded at position $position (max $maxBytes bytes)")
        }
        val b = inputStream.read()
        if (b == -1) throw ReaderException("Unexpected end of stream at position $position")
        position++
        return b.toByte()
    }

    override fun read(count: Int): ByteArray {
        if (count > maxBytes - position) {
            throw ReaderException(
                "Requested $count bytes at position $position exceeds limit of $maxBytes bytes"
            )
        }
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = inputStream.read(result, offset, count - offset)
            if (n == -1) throw ReaderException("Unexpected end of stream at position $position")
            offset += n
            position += n
        }
        return result
    }

    override fun readString(count: Int): String = read(count).decodeToString()

    override fun skip(count: Int) {
        if (count > maxBytes - position) {
            throw ReaderException(
                "Skip of $count bytes at position $position exceeds limit of $maxBytes bytes"
            )
        }
        var remaining = count
        while (remaining > 0) {
            val n = inputStream.skip(remaining.toLong()).toInt()
            if (n <= 0) {
                if (inputStream.read() == -1) throw ReaderException("Unexpected end of stream at position $position")
                remaining--
                position++
            } else {
                remaining -= n
                position += n
            }
        }
    }

    companion object {
        /** Default maximum frame size: 1 MiB. */
        const val DEFAULT_MAX_BYTES: Long = 1L shl 20
    }
}
