package dev.breischl.keneth.core.messages

import dev.breischl.keneth.core.values.SerializerUtils
import dev.breischl.keneth.core.values.SerializerUtils.asCborMap
import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
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
 * Session establishment message containing identity and capability information.
 *
 * This message is sent during session setup to exchange identifying information
 * between the client and server. Defined in EnergyNet Protocol section 4.2.
 *
 * @property identity The unique identifier for this device or endpoint (required).
 * @property type The type of device (e.g., "charger", "vehicle").
 * @property version The protocol or software version.
 * @property name A human-readable name for the device.
 * @property tenant The tenant or organization identifier.
 * @property provider The service provider identifier.
 * @property session A unique session identifier.
 */
data class SessionParameters(
    val identity: String,
    val type: String? = null,
    val version: String? = null,
    val name: String? = null,
    val tenant: String? = null,
    val provider: String? = null,
    val session: String? = null
) : Message() {
    override val typeId: UInt = TYPE_ID
    override val payloadSerializer get() = SessionParametersSerializer

    companion object {
        fun serializer(): KSerializer<SessionParameters> = SessionParametersSerializer

        const val TYPE_ID: UInt = 0xBABA_5E55u
        const val FIELD_IDENTITY: Int = 0x00
        const val FIELD_TYPE: Int = 0x01
        const val FIELD_VERSION: Int = 0x02
        const val FIELD_NAME: Int = 0x03
        const val FIELD_TENANT: Int = 0x04
        const val FIELD_PROVIDER: Int = 0x05
        const val FIELD_SESSION: Int = 0x06
    }

    object SessionParametersSerializer : KSerializer<SessionParameters> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SessionParameters")

        override fun serialize(encoder: Encoder, value: SessionParameters) {
            val map = CborObject.buildMap {
                putText(FIELD_IDENTITY, value.identity)
                value.type?.let { putText(FIELD_TYPE, it) }
                value.version?.let { putText(FIELD_VERSION, it) }
                value.name?.let { putText(FIELD_NAME, it) }
                value.tenant?.let { putText(FIELD_TENANT, it) }
                value.provider?.let { putText(FIELD_PROVIDER, it) }
                value.session?.let { putText(FIELD_SESSION, it) }
            }
            encoder.encodeSerializableValue(CborMap.serializer(), map)
        }

        override fun deserialize(decoder: Decoder): SessionParameters {
            val map = decoder.decodeSerializableValue(CborMap.serializer())
            return SessionParameters(
                identity = map.unwrapText(FIELD_IDENTITY)
                    ?: error("Missing identity field"),
                type = map.unwrapText(FIELD_TYPE),
                version = map.unwrapText(FIELD_VERSION),
                name = map.unwrapText(FIELD_NAME),
                tenant = map.unwrapText(FIELD_TENANT),
                provider = map.unwrapText(FIELD_PROVIDER),
                session = map.unwrapText(FIELD_SESSION)
            )
        }

        private fun MutableMap<CborObject, CborObject>.putText(fieldId: Int, value: String) {
            put(CborObject.positive(fieldId), SerializerUtils.buildStringMap(Text.TYPE_ID, value))
        }

        private fun CborMap.unwrapText(fieldId: Int): String? {
            return getByIntKey(fieldId)?.asCborMap()?.getByIntKey(Text.TYPE_ID)?.toStringValue()
        }
    }
}