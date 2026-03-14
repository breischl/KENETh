package dev.breischl.keneth.web.debugger

import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.values.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageCodecTest {

    /** Helper: encode a message to frame hex via [MessageCodec.encodeText]. */
    private fun messageToHex(message: Message): String {
        val text = when (message) {
            is Ping -> "Ping"
            is SessionParameters -> buildString {
                appendLine("SessionParameters")
                appendLine("identity: ${message.identity}")
                message.type?.let { appendLine("type: $it") }
                message.version?.let { appendLine("version: $it") }
                message.name?.let { appendLine("name: $it") }
                message.tenant?.let { appendLine("tenant: $it") }
                message.provider?.let { appendLine("provider: $it") }
                message.session?.let { appendLine("session: $it") }
            }.trimEnd()

            is SoftDisconnect -> buildString {
                appendLine("SoftDisconnect")
                message.reconnect?.let { appendLine("reconnect: $it") }
                message.reason?.let { appendLine("reason: $it") }
            }.trimEnd()

            is DemandParameters -> buildString {
                appendLine("DemandParameters")
                message.voltage?.let { appendLine("voltage: ${it.volts}") }
                message.current?.let { appendLine("current: ${it.amperes}") }
                message.voltageLimits?.let { appendLine("voltageLimits: ${it.volts}") }
                message.currentLimits?.let { appendLine("currentLimits: ${it.amperes}") }
                message.powerLimit?.let { appendLine("powerLimit: ${it.watts}") }
                message.duration?.let { appendLine("duration: ${it.millis}") }
            }.trimEnd()

            is SupplyParameters -> buildString {
                appendLine("SupplyParameters")
                message.voltageLimits?.let {
                    appendLine("voltageLimits.min: ${it.min.volts}")
                    appendLine("voltageLimits.max: ${it.max.volts}")
                }
                message.currentLimits?.let {
                    appendLine("currentLimits.min: ${it.min.amperes}")
                    appendLine("currentLimits.max: ${it.max.amperes}")
                }
                message.powerLimit?.let { appendLine("powerLimit: ${it.watts}") }
                message.voltage?.let { appendLine("voltage: ${it.volts}") }
                message.current?.let { appendLine("current: ${it.amperes}") }
            }.trimEnd()

            is StorageParameters -> buildString {
                appendLine("StorageParameters")
                message.soc?.let { appendLine("soc: ${it.percent}") }
                message.socTarget?.let { appendLine("socTarget: ${it.percent}") }
                message.socTargetTime?.let { appendLine("socTargetTime: ${it.millis}") }
                message.capacity?.let { appendLine("capacity: ${it.wattHours}") }
            }.trimEnd()

            is UnknownMessage -> throw IllegalArgumentException("Cannot encode UnknownMessage to text in tests")
        }
        return MessageCodec.encodeText(text).getOrThrow()
    }

    // --- decodeHex tests ---

    @Test
    fun decodeHex_ping_showsTypeName() {
        val hex = messageToHex(Ping)
        val result = MessageCodec.decodeHex(hex)
        assertTrue(result.isSuccess, "decode failed: ${result.exceptionOrNull()}")
        val text = result.getOrThrow()
        assertTrue(text.startsWith("Ping"), "Expected Ping header, got: $text")
    }

    @Test
    fun decodeHex_sessionParameters_showsAllFields() {
        val msg = SessionParameters(
            identity = "test-device",
            type = "charger",
            version = "1.0",
            name = "My Charger",
            tenant = "acme",
            provider = "energycorp",
            session = "sess-123"
        )
        val hex = messageToHex(msg)
        val result = MessageCodec.decodeHex(hex)
        assertTrue(result.isSuccess)
        val text = result.getOrThrow()
        assertTrue(text.startsWith("SessionParameters"), text)
        assertTrue(text.contains("identity: test-device"), text)
        assertTrue(text.contains("type: charger"), text)
        assertTrue(text.contains("version: 1.0"), text)
        assertTrue(text.contains("name: My Charger"), text)
        assertTrue(text.contains("tenant: acme"), text)
        assertTrue(text.contains("provider: energycorp"), text)
        assertTrue(text.contains("session: sess-123"), text)
    }

    @Test
    fun decodeHex_sessionParameters_omitsNullFields() {
        val msg = SessionParameters(identity = "minimal")
        val hex = messageToHex(msg)
        val text = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(text.contains("identity: minimal"), text)
        assertFalse(text.contains("type:"), text)
        assertFalse(text.contains("version:"), text)
    }

    @Test
    fun decodeHex_softDisconnect_showsFields() {
        val msg = SoftDisconnect(reconnect = true, reason = "firmware update")
        val hex = messageToHex(msg)
        val text = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(text.startsWith("SoftDisconnect"), text)
        assertTrue(text.contains("reconnect: true"), text)
        assertTrue(text.contains("reason: firmware update"), text)
    }

    @Test
    fun decodeHex_demandParameters_showsScalarFields() {
        val msg = DemandParameters(
            voltage = Voltage(400.0),
            current = Current(32.0),
            powerLimit = Power(10000.0),
            duration = Duration(3600000L)
        )
        val hex = messageToHex(msg)
        val text = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(text.startsWith("DemandParameters"), text)
        assertTrue(text.contains("voltage: 400.0"), text)
        assertTrue(text.contains("current: 32.0"), text)
        assertTrue(text.contains("powerLimit: 10000.0"), text)
        assertTrue(text.contains("duration: 3600000"), text)
    }

    @Test
    fun decodeHex_demandParameters_scalarVoltageLimits() {
        val msg = DemandParameters(voltageLimits = Voltage(500.0), currentLimits = Current(100.0))
        val hex = messageToHex(msg)
        val text = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(text.contains("voltageLimits: 500.0"), text)
        assertTrue(text.contains("currentLimits: 100.0"), text)
    }

    @Test
    fun decodeHex_supplyParameters_showsSimpleFieldsDropsComplex() {
        val msg = SupplyParameters(
            voltageLimits = Bounds(Voltage(200.0), Voltage(500.0)),
            currentLimits = Bounds(Current(1.0), Current(100.0)),
            powerLimit = Power(50000.0),
            voltage = Voltage(400.0),
            current = Current(50.0)
        )
        val hex = messageToHex(msg)
        val text = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(text.startsWith("SupplyParameters"), text)
        assertTrue(text.contains("powerLimit: 50000.0"), text)
        assertTrue(text.contains("voltage: 400.0"), text)
        assertTrue(text.contains("current: 50.0"), text)
        // Bounds are complex — shown with bracket notation
        assertTrue(text.contains("voltageLimits.min: 200.0"), text)
        assertTrue(text.contains("voltageLimits.max: 500.0"), text)
        assertTrue(text.contains("currentLimits.min: 1.0"), text)
        assertTrue(text.contains("currentLimits.max: 100.0"), text)
    }

    @Test
    fun decodeHex_storageParameters_showsScalarFieldsDropsComplex() {
        val msg = StorageParameters(
            soc = Percentage(80.0),
            socTarget = Percentage(100.0),
            socTargetTime = Duration(7200000L),
            capacity = Energy(60000.0)
        )
        val hex = messageToHex(msg)
        val text = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(text.startsWith("StorageParameters"), text)
        assertTrue(text.contains("soc: 80.0"), text)
        assertTrue(text.contains("socTarget: 100.0"), text)
        assertTrue(text.contains("socTargetTime: 7200000"), text)
        assertTrue(text.contains("capacity: 60000.0"), text)
    }

    @Test
    fun decodeHex_invalidHex_returnsFailure() {
        val result = MessageCodec.decodeHex("ZZZZ")
        assertTrue(result.isFailure)
    }

    @Test
    fun decodeHex_invalidFrame_returnsFailure() {
        val result = MessageCodec.decodeHex("DEADBEEF")
        assertTrue(result.isFailure)
    }

    // --- encodeText tests ---

    @Test
    fun encodeText_ping_producesValidFrame() {
        val text = "Ping"
        val result = MessageCodec.encodeText(text)
        assertTrue(result.isSuccess, "encode failed: ${result.exceptionOrNull()}")
        // Decode it back to verify
        val decoded = MessageCodec.decodeHex(result.getOrThrow())
        assertTrue(decoded.isSuccess)
        assertTrue(decoded.getOrThrow().startsWith("Ping"))
    }

    @Test
    fun encodeText_sessionParameters_roundtrip() {
        val original = """
            SessionParameters
            identity: test-device
            type: charger
        """.trimIndent()
        val hex = MessageCodec.encodeText(original).getOrThrow()
        val decoded = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(decoded.contains("identity: test-device"), decoded)
        assertTrue(decoded.contains("type: charger"), decoded)
    }

    @Test
    fun encodeText_softDisconnect_roundtrip() {
        val original = """
            SoftDisconnect
            reconnect: false
            reason: maintenance
        """.trimIndent()
        val hex = MessageCodec.encodeText(original).getOrThrow()
        val decoded = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(decoded.contains("reconnect: false"), decoded)
        assertTrue(decoded.contains("reason: maintenance"), decoded)
    }

    @Test
    fun encodeText_demandParameters_roundtrip() {
        val original = """
            DemandParameters
            voltage: 400.0
            current: 32.0
            powerLimit: 10000.0
            duration: 3600000
        """.trimIndent()
        val hex = MessageCodec.encodeText(original).getOrThrow()
        val decoded = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(decoded.contains("voltage: 400.0"), decoded)
        assertTrue(decoded.contains("current: 32.0"), decoded)
        assertTrue(decoded.contains("powerLimit: 10000.0"), decoded)
        assertTrue(decoded.contains("duration: 3600000"), decoded)
    }

    @Test
    fun encodeText_supplyParameters_simpleFieldsRoundtrip() {
        val original = """
            SupplyParameters
            powerLimit: 50000.0
            voltage: 400.0
            current: 50.0
        """.trimIndent()
        val hex = MessageCodec.encodeText(original).getOrThrow()
        val decoded = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(decoded.contains("powerLimit: 50000.0"), decoded)
        assertTrue(decoded.contains("voltage: 400.0"), decoded)
        assertTrue(decoded.contains("current: 50.0"), decoded)
    }

    @Test
    fun encodeText_supplyParameters_boundsRoundtrip() {
        val original = """
            SupplyParameters
            voltageLimits.min: 200.0
            voltageLimits.max: 500.0
            currentLimits.min: 1.0
            currentLimits.max: 100.0
        """.trimIndent()
        val hex = MessageCodec.encodeText(original).getOrThrow()
        val decoded = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(decoded.contains("voltageLimits.min: 200.0"), decoded)
        assertTrue(decoded.contains("voltageLimits.max: 500.0"), decoded)
        assertTrue(decoded.contains("currentLimits.min: 1.0"), decoded)
        assertTrue(decoded.contains("currentLimits.max: 100.0"), decoded)
    }

    @Test
    fun encodeText_storageParameters_roundtrip() {
        val original = """
            StorageParameters
            soc: 80.0
            socTarget: 100.0
            socTargetTime: 7200000
            capacity: 60000.0
        """.trimIndent()
        val hex = MessageCodec.encodeText(original).getOrThrow()
        val decoded = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(decoded.contains("soc: 80.0"), decoded)
        assertTrue(decoded.contains("socTarget: 100.0"), decoded)
        assertTrue(decoded.contains("socTargetTime: 7200000"), decoded)
        assertTrue(decoded.contains("capacity: 60000.0"), decoded)
    }

    @Test
    fun encodeText_unknownTypeName_returnsFailure() {
        val result = MessageCodec.encodeText("FakeMessage\nfoo: bar")
        assertTrue(result.isFailure)
    }

    @Test
    fun encodeText_emptyInput_returnsFailure() {
        val result = MessageCodec.encodeText("")
        assertTrue(result.isFailure)
    }

    @Test
    fun encodeText_malformedFieldLine_returnsFailure() {
        val result = MessageCodec.encodeText("DemandParameters\nvoltage 400.0")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("key: value"), result.exceptionOrNull()!!.message)
    }

    // --- Full roundtrip: message → hex → text → hex → text should be stable ---

    @Test
    fun roundtrip_allFieldsSessionParameters_stableAfterTwoRoundtrips() {
        val msg = SessionParameters(
            identity = "device-1",
            type = "battery",
            version = "2.0",
            name = "Test Battery",
            tenant = "tenant-x",
            provider = "provider-y",
            session = "s-001"
        )
        val hex1 = messageToHex(msg)
        val text1 = MessageCodec.decodeHex(hex1).getOrThrow()
        val hex2 = MessageCodec.encodeText(text1).getOrThrow()
        val text2 = MessageCodec.decodeHex(hex2).getOrThrow()
        assertEquals(text1, text2, "Text should be stable across roundtrips")
    }

    @Test
    fun roundtrip_ping_stableAfterTwoRoundtrips() {
        val hex1 = messageToHex(Ping)
        val text1 = MessageCodec.decodeHex(hex1).getOrThrow()
        val hex2 = MessageCodec.encodeText(text1).getOrThrow()
        val text2 = MessageCodec.decodeHex(hex2).getOrThrow()
        assertEquals(text1, text2)
    }

    @Test
    fun roundtrip_demandParametersAllFields_stableAfterTwoRoundtrips() {
        val msg = DemandParameters(
            voltage = Voltage(400.0),
            current = Current(32.0),
            voltageLimits = Voltage(500.0),
            currentLimits = Current(100.0),
            powerLimit = Power(10000.0),
            duration = Duration(3600000L)
        )
        val hex1 = messageToHex(msg)
        val text1 = MessageCodec.decodeHex(hex1).getOrThrow()
        val hex2 = MessageCodec.encodeText(text1).getOrThrow()
        val text2 = MessageCodec.decodeHex(hex2).getOrThrow()
        assertEquals(text1, text2)
    }

    @Test
    fun encodeText_demandParameters_scalarLimitsRoundtrip() {
        val original = """
            DemandParameters
            voltageLimits: 500.0
            currentLimits: 100.0
        """.trimIndent()
        val hex = MessageCodec.encodeText(original).getOrThrow()
        val decoded = MessageCodec.decodeHex(hex).getOrThrow()
        assertTrue(decoded.contains("voltageLimits: 500.0"), decoded)
        assertTrue(decoded.contains("currentLimits: 100.0"), decoded)
    }
}
