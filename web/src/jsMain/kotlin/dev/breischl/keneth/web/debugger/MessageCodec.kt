package dev.breischl.keneth.web.debugger

import dev.breischl.keneth.core.frames.FrameCodec
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.parsing.LenientMessageParser
import dev.breischl.keneth.core.values.*

/**
 * Codec for converting EP messages between hex-encoded frame bytes and a
 * human-readable flat text format.
 *
 * The text format has the message type name on the first line, followed by
 * `key: value` pairs on subsequent lines. Nested fields use dot notation
 * (e.g., `voltageLimits.min: 200.0`).
 *
 * Complex composite fields (powerMix, energyMix, energyPrices, isolation)
 * are silently dropped in v1 — they don't appear in text output and cannot
 * be encoded from text.
 */
object MessageCodec {

    private val parser = LenientMessageParser()

    /**
     * Decodes a hex-encoded EP frame into a human-readable flat text representation.
     *
     * @param hex The hex string of the full EP frame (magic bytes + headers + typeId + payload).
     * @return A [Result] containing the flat text, or a failure with a descriptive error.
     */
    fun decodeHex(hex: String): Result<String> {
        val bytes = HexCodec.decode(hex).getOrElse { return Result.failure(it) }

        val frameResult = FrameCodec.decode(bytes)
        if (!frameResult.succeeded) {
            val errors = frameResult.diagnostics.joinToString("; ") { it.message }
            return Result.failure(IllegalArgumentException("Frame decode failed: $errors"))
        }

        val frame = frameResult.value!!
        val messageResult = parser.parseMessage(frame)
        if (!messageResult.succeeded) {
            val errors = messageResult.diagnostics.joinToString("; ") { it.message }
            return Result.failure(IllegalArgumentException("Message parse failed: $errors"))
        }

        return Result.success(messageToText(messageResult.value!!))
    }

    /**
     * Encodes a human-readable flat text representation into a hex-encoded EP frame.
     *
     * @param text The flat text with type name on the first line and `key: value` pairs.
     * @return A [Result] containing the hex string of the full EP frame.
     */
    fun encodeText(text: String): Result<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty input"))
        }

        val typeName = lines.first()
        val fields = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            val colonIndex = line.indexOf(':')
            if (colonIndex < 0) {
                return Result.failure(
                    IllegalArgumentException("Invalid field line (expected 'key: value'): $line")
                )
            }
            val key = line.substring(0, colonIndex).trim()
            val value = line.substring(colonIndex + 1).trim()
            fields[key] = value
        }

        val message = try {
            textToMessage(typeName, fields)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Failed to build message: ${e.message}", e))
        }

        return Result.success(HexCodec.encode(MessageEncoder.encode(message)))
    }

    // --- Message → Text ---

    private fun messageToText(message: Message): String {
        val sb = StringBuilder()
        when (message) {
            is Ping -> sb.appendLine("Ping")
            is SessionParameters -> sessionParametersToText(message, sb)
            is SoftDisconnect -> softDisconnectToText(message, sb)
            is DemandParameters -> demandParametersToText(message, sb)
            is SupplyParameters -> supplyParametersToText(message, sb)
            is StorageParameters -> storageParametersToText(message, sb)
            is UnknownMessage -> unknownMessageToText(message, sb)
        }
        return sb.toString().trimEnd()
    }

    private fun sessionParametersToText(msg: SessionParameters, sb: StringBuilder) {
        sb.appendLine("SessionParameters")
        sb.appendField("identity", msg.identity)
        msg.type?.let { sb.appendField("type", it) }
        msg.version?.let { sb.appendField("version", it) }
        msg.name?.let { sb.appendField("name", it) }
        msg.tenant?.let { sb.appendField("tenant", it) }
        msg.provider?.let { sb.appendField("provider", it) }
        msg.session?.let { sb.appendField("session", it) }
    }

    private fun softDisconnectToText(msg: SoftDisconnect, sb: StringBuilder) {
        sb.appendLine("SoftDisconnect")
        msg.reconnect?.let { sb.appendField("reconnect", it.toString()) }
        msg.reason?.let { sb.appendField("reason", it) }
    }

    private fun demandParametersToText(msg: DemandParameters, sb: StringBuilder) {
        sb.appendLine("DemandParameters")
        msg.voltage?.let { sb.appendField("voltage", formatDouble(it.volts)) }
        msg.current?.let { sb.appendField("current", formatDouble(it.amperes)) }
        msg.voltageLimits?.let { sb.appendField("voltageLimits", formatDouble(it.volts)) }
        msg.currentLimits?.let { sb.appendField("currentLimits", formatDouble(it.amperes)) }
        msg.powerLimit?.let { sb.appendField("powerLimit", formatDouble(it.watts)) }
        msg.duration?.let { sb.appendField("duration", it.millis.toString()) }
    }

    private fun supplyParametersToText(msg: SupplyParameters, sb: StringBuilder) {
        sb.appendLine("SupplyParameters")
        msg.voltageLimits?.let {
            sb.appendField("voltageLimits.min", formatDouble(it.min.volts))
            sb.appendField("voltageLimits.max", formatDouble(it.max.volts))
        }
        msg.currentLimits?.let {
            sb.appendField("currentLimits.min", formatDouble(it.min.amperes))
            sb.appendField("currentLimits.max", formatDouble(it.max.amperes))
        }
        msg.powerLimit?.let { sb.appendField("powerLimit", formatDouble(it.watts)) }
        // powerMix, energyPrices, isolation: silently dropped (v1)
        msg.voltage?.let { sb.appendField("voltage", formatDouble(it.volts)) }
        msg.current?.let { sb.appendField("current", formatDouble(it.amperes)) }
    }

    private fun storageParametersToText(msg: StorageParameters, sb: StringBuilder) {
        sb.appendLine("StorageParameters")
        msg.soc?.let { sb.appendField("soc", formatDouble(it.percent)) }
        msg.socTarget?.let { sb.appendField("socTarget", formatDouble(it.percent)) }
        msg.socTargetTime?.let { sb.appendField("socTargetTime", it.millis.toString()) }
        msg.capacity?.let { sb.appendField("capacity", formatDouble(it.wattHours)) }
        // energyMix: silently dropped (v1)
    }

    private fun unknownMessageToText(msg: UnknownMessage, sb: StringBuilder) {
        sb.appendLine("UnknownMessage")
        sb.appendField("typeId", "0x${msg.typeId.toString(16).padStart(8, '0')}")
        sb.appendField("payload", HexCodec.encode(msg.rawPayload))
    }

    // --- Text → Message ---

    private fun textToMessage(typeName: String, fields: Map<String, String>): Message {
        return when (typeName) {
            "Ping" -> Ping
            "SessionParameters" -> textToSessionParameters(fields)
            "SoftDisconnect" -> textToSoftDisconnect(fields)
            "DemandParameters" -> textToDemandParameters(fields)
            "SupplyParameters" -> textToSupplyParameters(fields)
            "StorageParameters" -> textToStorageParameters(fields)
            else -> throw IllegalArgumentException("Unknown message type: $typeName")
        }
    }

    private fun textToSessionParameters(fields: Map<String, String>): SessionParameters {
        return SessionParameters(
            identity = fields["identity"] ?: throw IllegalArgumentException("Missing required field: identity"),
            type = fields["type"],
            version = fields["version"],
            name = fields["name"],
            tenant = fields["tenant"],
            provider = fields["provider"],
            session = fields["session"]
        )
    }

    private fun textToSoftDisconnect(fields: Map<String, String>): SoftDisconnect {
        return SoftDisconnect(
            reconnect = fields["reconnect"]?.toBooleanStrictOrNull(),
            reason = fields["reason"]
        )
    }

    private fun textToDemandParameters(fields: Map<String, String>): DemandParameters {
        return DemandParameters(
            voltage = fields["voltage"]?.toDoubleOrNull()?.let { Voltage(it) },
            current = fields["current"]?.toDoubleOrNull()?.let { Current(it) },
            voltageLimits = fields["voltageLimits"]?.toDoubleOrNull()?.let { Voltage(it) },
            currentLimits = fields["currentLimits"]?.toDoubleOrNull()?.let { Current(it) },
            powerLimit = fields["powerLimit"]?.toDoubleOrNull()?.let { Power(it) },
            duration = fields["duration"]?.toLongOrNull()?.let { Duration(it) }
        )
    }

    private fun textToSupplyParameters(fields: Map<String, String>): SupplyParameters {
        val voltageBoundsMin = fields["voltageLimits.min"]?.toDoubleOrNull()
        val voltageBoundsMax = fields["voltageLimits.max"]?.toDoubleOrNull()
        val currentBoundsMin = fields["currentLimits.min"]?.toDoubleOrNull()
        val currentBoundsMax = fields["currentLimits.max"]?.toDoubleOrNull()

        return SupplyParameters(
            voltageLimits = if (voltageBoundsMin != null && voltageBoundsMax != null)
                Bounds(Voltage(voltageBoundsMin), Voltage(voltageBoundsMax)) else null,
            currentLimits = if (currentBoundsMin != null && currentBoundsMax != null)
                Bounds(Current(currentBoundsMin), Current(currentBoundsMax)) else null,
            powerLimit = fields["powerLimit"]?.toDoubleOrNull()?.let { Power(it) },
            // powerMix, energyPrices, isolation: not supported in v1 text format
            voltage = fields["voltage"]?.toDoubleOrNull()?.let { Voltage(it) },
            current = fields["current"]?.toDoubleOrNull()?.let { Current(it) }
        )
    }

    private fun textToStorageParameters(fields: Map<String, String>): StorageParameters {
        return StorageParameters(
            soc = fields["soc"]?.toDoubleOrNull()?.let { Percentage(it) },
            socTarget = fields["socTarget"]?.toDoubleOrNull()?.let { Percentage(it) },
            socTargetTime = fields["socTargetTime"]?.toLongOrNull()?.let { Duration(it) },
            capacity = fields["capacity"]?.toDoubleOrNull()?.let { Energy(it) }
            // energyMix: not supported in v1 text format
        )
    }

    // --- Helpers ---

    private fun StringBuilder.appendField(key: String, value: String) {
        appendLine("$key: $value")
    }

    private fun formatDouble(value: Double): String {
        // If the value is a whole number, still show .0 for clarity
        return if (value == value.toLong().toDouble() && !value.isInfinite() && !value.isNaN()) {
            "${value.toLong()}.0"
        } else {
            value.toString()
        }
    }
}
