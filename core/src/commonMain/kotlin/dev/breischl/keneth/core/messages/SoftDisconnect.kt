package dev.breischl.keneth.core.messages

import dev.breischl.keneth.core.values.Flag
import dev.breischl.keneth.core.values.SerializerUtils
import dev.breischl.keneth.core.values.SerializerUtils.asCborMap
import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toBooleanValue
import dev.breischl.keneth.core.values.SerializerUtils.toStringValue
import dev.breischl.keneth.core.values.Text
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap
import net.orandja.obor.data.CborObject

/**
 * Graceful disconnection message.
 *
 * This message signals an intentional end to the session, optionally
 * indicating whether the client should attempt to reconnect.
 * Defined in EnergyNet Protocol section 4.3.
 *
 * @property reconnect If true, the client should attempt to reconnect.
 * @property reason A human-readable reason for the disconnection.
 */
data class SoftDisconnect(
    val reconnect: Boolean? = null,
    val reason: String? = null
) : Message() {
    override val typeId: UInt = TYPE_ID
    override val payloadSerializer get() = SoftDisconnectSerializer

    companion object {
        fun serializer() = SoftDisconnectSerializer
        const val TYPE_ID = 0xBABA_DEADu
        const val FIELD_RECONNECT: Int = 0x00
        const val FIELD_REASON: Int = 0x01
    }

    object SoftDisconnectSerializer : KSerializer<SoftDisconnect> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SoftDisconnect")

        override fun serialize(encoder: Encoder, value: SoftDisconnect) {
            val map = CborObject.buildMap {
                value.reconnect?.let {
                    put(CborObject.positive(FIELD_RECONNECT), SerializerUtils.buildBooleanMap(Flag.TYPE_ID, it))
                }
                value.reason?.let {
                    put(CborObject.positive(FIELD_REASON), SerializerUtils.buildStringMap(Text.TYPE_ID, it))
                }
            }
            encoder.encodeSerializableValue(CborMap.serializer(), map)
        }

        override fun deserialize(decoder: Decoder): SoftDisconnect {
            val map = decoder.decodeSerializableValue(CborMap.serializer())
            return SoftDisconnect(
                reconnect = map.getByIntKey(FIELD_RECONNECT)?.asCborMap()?.getByIntKey(Flag.TYPE_ID)?.toBooleanValue(),
                reason = map.getByIntKey(FIELD_REASON)?.asCborMap()?.getByIntKey(Text.TYPE_ID)?.toStringValue()
            )
        }
    }
}