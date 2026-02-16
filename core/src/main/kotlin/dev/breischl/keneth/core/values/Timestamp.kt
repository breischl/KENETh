package dev.breischl.keneth.core.values

import kotlin.time.Instant
import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toStringValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap

/**
 * Represents a point in time as a timestamp.
 *
 * This is an inline value class wrapping a [kotlin.time.Instant] for type safety.
 * The timestamp is serialized as an ISO-8601 text string per the EnergyNet Protocol spec.
 * Defined in EnergyNet Protocol section 3.1.
 *
 * @property instant The instant representing the point in time.
 */
@JvmInline
@Serializable(with = TimestampSerializer::class)
value class Timestamp(val instant: Instant) {
    companion object {
        /** CBOR type identifier for Timestamp values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x03
    }
}

/**
 * CBOR serializer for [Timestamp] values.
 *
 * Serializes timestamp as a CBOR map with the type ID as key: `{ 0x03: <isoString> }`.
 * The instant is converted to/from ISO-8601 format for serialization per RFC 7049.
 */
object TimestampSerializer : KSerializer<Timestamp> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Timestamp")

    override fun serialize(encoder: Encoder, value: Timestamp) {
        encoder.encodeSerializableValue(
            CborMap.serializer(),
            SerializerUtils.buildStringMap(Timestamp.TYPE_ID, value.instant.toString())
        )
    }

    override fun deserialize(decoder: Decoder): Timestamp {
        val map = decoder.decodeSerializableValue(CborMap.serializer())
        val isoString = map.getByIntKey(Timestamp.TYPE_ID) ?: error("Missing timestamp value")
        return Timestamp(Instant.parse(isoString.toStringValue()))
    }
}
