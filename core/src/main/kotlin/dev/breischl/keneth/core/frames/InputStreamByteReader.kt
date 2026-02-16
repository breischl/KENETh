package dev.breischl.keneth.core.frames

import net.orandja.obor.io.ByteReader
import net.orandja.obor.io.ReaderException
import java.io.InputStream

/**
 * A [ByteReader] that reads from a [java.io.InputStream].
 */
internal class InputStreamByteReader(
    private val inputStream: InputStream
) : ByteReader {
    private var position = 0L

    override fun totalRead(): Long = position

    override fun read(): Byte {
        val b = inputStream.read()
        if (b == -1) throw ReaderException("Unexpected end of stream at position $position")
        position++
        return b.toByte()
    }

    override fun read(count: Int): ByteArray {
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
}
