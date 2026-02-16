package dev.breischl.keneth.core.parsing

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.DemandParameters
import dev.breischl.keneth.core.messages.Ping
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.SoftDisconnect
import dev.breischl.keneth.core.messages.StorageParameters
import dev.breischl.keneth.core.messages.SupplyParameters
import dev.breischl.keneth.core.messages.UnknownMessage
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageParserTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }
    private val lenientParser = LenientMessageParser()
    private val strictParser = StrictMessageParser()

    @Test
    fun `lenient parser parses Ping message`() {
        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = Ping.typeId,
            payload = byteArrayOf()
        )

        val result = lenientParser.parseMessage(frame)

        assertTrue(result.succeeded)
        assertEquals(Ping, result.value)
    }

    @Test
    fun `lenient parser parses SessionParameters message`() {
        val sessionParams = SessionParameters(
            identity = "test-device",
            type = "charger"
        )
        val payload = cbor.encodeToByteArray(SessionParameters.serializer(), sessionParams)

        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = sessionParams.typeId,
            payload = payload
        )

        val result = lenientParser.parseMessage(frame)

        assertTrue(result.succeeded)
        assertEquals(sessionParams, result.value)
    }

    @Test
    fun `lenient parser handles unknown message type with warning`() {
        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = 0x12345678u,
            payload = byteArrayOf(0x01, 0x02, 0x03)
        )

        val result = lenientParser.parseMessage(frame)

        assertTrue(result.succeeded)
        assertTrue(result.hasWarnings)
        assertFalse(result.hasErrors)
        assertTrue(result.value is UnknownMessage)

        assertEquals(0x12345678u, result.value.typeId)
        assertTrue(result.value.rawPayload.contentEquals(byteArrayOf(0x01, 0x02, 0x03)))
    }

    @Test
    fun `strict parser fails on unknown message type`() {
        val frame = Frame(
            headers = emptyMap(),
            messageTypeId = 0x12345678u,
            payload = byteArrayOf(0x01, 0x02, 0x03)
        )

        val result = strictParser.parseMessage(frame)

        assertFalse(result.succeeded)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `MessageRegistry knows expected message types`() {
        assertTrue(MessageRegistry.isKnownType(Ping.typeId))
        assertTrue(MessageRegistry.isKnownType(SessionParameters("").typeId))
        assertTrue(MessageRegistry.isKnownType(SoftDisconnect().typeId))
        assertTrue(MessageRegistry.isKnownType(SupplyParameters().typeId))
        assertTrue(MessageRegistry.isKnownType(DemandParameters().typeId))
        assertTrue(MessageRegistry.isKnownType(StorageParameters().typeId))
        assertFalse(MessageRegistry.isKnownType(0x00000000u))
    }
}
