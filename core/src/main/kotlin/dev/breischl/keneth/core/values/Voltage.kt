package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toDoubleValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap

/**
 * Represents an electrical voltage measurement in volts.
 *
 * This is an inline value class for type safety, ensuring that voltage values
 * cannot be accidentally confused with other numeric types like current or power.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property volts The voltage value in volts (V).
 */
@JvmInline
@Serializable(with = VoltageSerializer::class)
value class Voltage(val volts: Double) {
    companion object {
        /** CBOR type identifier for Voltage values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x10
    }
}

/**
 * CBOR serializer for [Voltage] values.
 *
 * Serializes voltage as a CBOR map with the type ID as key: `{ 0x10: <volts> }`.
 * Accepts any numeric CBOR type on deserialization (int, float16, float32, float64).
 */
object VoltageSerializer : KSerializer<Voltage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Voltage")

    override fun serialize(encoder: Encoder, value: Voltage) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildDoubleMap(Voltage.TYPE_ID, value.volts)
        )
    }

    override fun deserialize(decoder: Decoder): Voltage {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Voltage.TYPE_ID) ?: error("Missing voltage value")
        return Voltage(value.toDoubleValue())
    }
}
