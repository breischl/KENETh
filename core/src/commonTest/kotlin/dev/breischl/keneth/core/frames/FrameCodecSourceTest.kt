package dev.breischl.keneth.core.frames

import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameCodecSourceTest {

    private fun encodeFrame(
        messageTypeId: UInt = 0x00000001u,
        payload: ByteArray = byteArrayOf(),
    ): ByteArray = FrameCodec.encode(Frame(emptyMap(), messageTypeId, payload))

    @Test
    fun `decodeFromSource on empty source returns null`() {
        val source = Buffer()

        val result = FrameCodec.decodeFromSource(source)

        assertNull(result)
    }

    @Test
    fun `decodeFromSource after consuming a frame on exhausted source returns null`() {
        val source = Buffer()
        source.write(encodeFrame())
        FrameCodec.decodeFromSource(source)

        val result = FrameCodec.decodeFromSource(source)

        assertNull(result)
    }

    @Test
    fun `decodeFromSource with valid frame returns decoded frame`() {
        val original = Frame(emptyMap(), 0xDEAD_BEEFu, byteArrayOf(0x01, 0x02, 0x03))
        val source = Buffer()
        source.write(FrameCodec.encode(original))

        val result = FrameCodec.decodeFromSource(source)!!

        assertTrue(result.succeeded)
        assertEquals(original.messageTypeId, result.value!!.messageTypeId)
        assertTrue(original.payload.contentEquals(result.value.payload))
    }

    @Test
    fun `decodeFromSource with truncated frame returns error result`() {
        val encoded = encodeFrame(payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        val truncated = encoded.copyOfRange(0, encoded.size - 3)
        val source = Buffer()
        source.write(truncated)

        val result = FrameCodec.decodeFromSource(source)!!

        assertFalse(result.succeeded)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `decodeFromSource with multiple frames decodes all in order`() {
        val frame1 = Frame(emptyMap(), 0x00000001u, byteArrayOf(0xAA.toByte()))
        val frame2 = Frame(emptyMap(), 0x00000002u, byteArrayOf(0xBB.toByte()))
        val source = Buffer()
        source.write(FrameCodec.encode(frame1))
        source.write(FrameCodec.encode(frame2))

        val result1 = FrameCodec.decodeFromSource(source)!!
        val result2 = FrameCodec.decodeFromSource(source)!!
        val result3 = FrameCodec.decodeFromSource(source)

        assertTrue(result1.succeeded)
        assertEquals(frame1.messageTypeId, result1.value!!.messageTypeId)
        assertTrue(result2.succeeded)
        assertEquals(frame2.messageTypeId, result2.value!!.messageTypeId)
        assertNull(result3)
    }
}
