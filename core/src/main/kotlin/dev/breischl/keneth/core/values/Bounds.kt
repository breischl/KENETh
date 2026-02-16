package dev.breischl.keneth.core.values

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a range with minimum and maximum bounds.
 *
 * This generic class is used to specify acceptable ranges for values like
 * voltage limits, current limits, or other bounded parameters in the
 * EnergyNet Protocol. Defined in EnergyNet Protocol section 3.1.1.
 *
 * @param T The type of value being bounded (e.g., [Voltage], [Current]).
 * @property min The minimum value of the range.
 * @property max The maximum value of the range.
 */
@Serializable(with = BoundsSerializer::class)
data class Bounds<T>(
    val min: T,
    val max: T
) {
    companion object {
        /** CBOR type identifier for Bounds values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x20
    }
}

/**
 * CBOR serializer for [Bounds] values.
 *
 * Serializes bounds as a CBOR map with the type ID as key, containing a
 * two-element list: `{ 0x20: [<min>, <max>] }`.
 *
 * @param T The type of value being bounded.
 * @param elementSerializer The serializer for the element type.
 */
class BoundsSerializer<T>(
    private val elementSerializer: KSerializer<T>
) : KSerializer<Bounds<T>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Bounds")

    private val mapSerializer = MapSerializer(Int.serializer(), ListSerializer(elementSerializer))

    override fun serialize(encoder: Encoder, value: Bounds<T>) {
        encoder.encodeSerializableValue(mapSerializer, mapOf(Bounds.TYPE_ID to listOf(value.min, value.max)))
    }

    override fun deserialize(decoder: Decoder): Bounds<T> {
        val map = decoder.decodeSerializableValue(mapSerializer)
        val list = map[Bounds.TYPE_ID] ?: error("Missing bounds value")
        require(list.size == 2) { "Bounds must have exactly 2 elements" }
        return Bounds(list[0], list[1])
    }

    companion object {
        /** Pre-configured serializer for voltage bounds. */
        val voltage = BoundsSerializer(VoltageSerializer)

        /** Pre-configured serializer for current bounds. */
        val current = BoundsSerializer(CurrentSerializer)

        /** Pre-configured serializer for power bounds. */
        val power = BoundsSerializer(PowerSerializer)

        /** Pre-configured serializer for percentage bounds. */
        val percentage = BoundsSerializer(PercentageSerializer)
    }
}
