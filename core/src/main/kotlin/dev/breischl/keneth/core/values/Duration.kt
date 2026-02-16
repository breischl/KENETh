package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toLongValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap

/**
 * Represents a time duration in milliseconds.
 *
 * This is an inline value class for type safety. Use this for representing
 * time intervals, timeouts, or expected durations in the EnergyNet Protocol.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property millis The duration value in milliseconds.
 */
@JvmInline
@Serializable(with = DurationSerializer::class)
value class Duration(val millis: Long) {
    companion object {
        /** CBOR type identifier for Duration values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x06
    }
}

/**
 * CBOR serializer for [Duration] values.
 *
 * Serializes duration as a CBOR map with the type ID as key: `{ 0x06: <millis> }`.
 */
object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Duration")

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildLongMap(Duration.TYPE_ID, value.millis)
        )
    }

    override fun deserialize(decoder: Decoder): Duration {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val value = map.getByIntKey(Duration.TYPE_ID) ?: error("Missing duration value")
        return Duration(value.toLongValue())
    }
}
