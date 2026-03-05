package dev.breischl.keneth.core.frames

import net.orandja.obor.io.ByteReader

/**
 * A [ByteReader] that serves a prefix [ByteArray] before delegating to another [ByteReader].
 *
 * Used to prepend already-consumed header bytes back before passing the combined
 * stream to OBOR for full CBOR structure decoding.
 */
internal class PrefixByteReader(
    private val prefix: ByteArray,
    private val delegate: ByteReader,
) : ByteReader {
    private var prefixPos = 0

    override fun totalRead(): Long = delegate.totalRead() + prefixPos

    override fun read(): Byte =
        if (prefixPos < prefix.size) prefix[prefixPos++]
        else delegate.read()

    override fun read(count: Int): ByteArray {
        if (prefixPos >= prefix.size) return delegate.read(count)
        val fromPrefix = minOf(count, prefix.size - prefixPos)
        val prefixPart = prefix.copyOfRange(prefixPos, prefixPos + fromPrefix)
        prefixPos += fromPrefix
        return if (fromPrefix == count) prefixPart
        else prefixPart + delegate.read(count - fromPrefix)
    }

    override fun readString(count: Int): String = read(count).decodeToString()

    override fun skip(count: Int) {
        if (prefixPos < prefix.size) {
            val fromPrefix = minOf(count, prefix.size - prefixPos)
            prefixPos += fromPrefix
            if (fromPrefix < count) delegate.skip(count - fromPrefix)
        } else {
            delegate.skip(count)
        }
    }
}
