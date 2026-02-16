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
 * Represents an energy measurement in watt-hours.
 *
 * This is an inline value class for type safety, ensuring that energy values
 * cannot be accidentally confused with other numeric types like power or voltage.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property wattHours The energy value in watt-hours (Wh).
 */
@JvmInline
@Serializable(with = EnergySerializer::class)
value class Energy(val wattHours: Double) {
    companion object {
        /** CBOR type identifier for Energy values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x13
    }
}

/**
 * CBOR serializer for [Energy] values.
 *
 * Serializes energy as a CBOR map with the type ID as key: `{ 0x13: <wattHours> }`.
 */
object EnergySerializer : KSerializer<Energy> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Energy")

    override fun serialize(encoder: Encoder, value: Energy) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildDoubleMap(Energy.TYPE_ID, value.wattHours)
        )
    }

    override fun deserialize(decoder: Decoder): Energy {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Energy.TYPE_ID) ?: error("Missing energy value")
        return Energy(value.toDoubleValue())
    }
}
