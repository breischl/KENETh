package dev.breischl.keneth.core.messages

import dev.breischl.keneth.core.values.Current
import dev.breischl.keneth.core.values.CurrentSerializer
import dev.breischl.keneth.core.values.Duration
import dev.breischl.keneth.core.values.DurationSerializer
import dev.breischl.keneth.core.values.Power
import dev.breischl.keneth.core.values.PowerSerializer
import dev.breischl.keneth.core.values.Voltage
import dev.breischl.keneth.core.values.VoltageSerializer
import dev.breischl.keneth.core.values.SerializerUtils
import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborMap
import net.orandja.obor.data.CborObject

/**
 * Power demand request from the vehicle or load.
 *
 * This message is sent by the vehicle (demand side) to request power
 * and communicate its requirements and limits.
 * Defined in EnergyNet Protocol section 4.5.
 *
 * @property voltage The requested voltage.
 * @property current The requested current.
 * @property voltageLimits The voltage limits the vehicle can accept.
 * @property currentLimits The current limits the vehicle can accept.
 * @property powerLimit The maximum power the vehicle can accept.
 * @property duration The expected duration of the charging session.
 */
data class DemandParameters(
    val voltage: Voltage? = null,
    val current: Current? = null,
    val voltageLimits: Voltage? = null,
    val currentLimits: Current? = null,
    val powerLimit: Power? = null,
    val duration: Duration? = null
) : Message() {
    override val typeId: UInt = TYPE_ID
    override val payloadSerializer get() = DemandParametersSerializer

    companion object {
        fun serializer() = DemandParametersSerializer
        const val TYPE_ID: UInt = 0xDCDC_FEEDu
        const val FIELD_VOLTAGE: Int = 0x00
        const val FIELD_CURRENT: Int = 0x01
        const val FIELD_VOLTAGE_LIMITS: Int = 0x02
        const val FIELD_CURRENT_LIMITS: Int = 0x03
        const val FIELD_POWER_LIMIT: Int = 0x04
        const val FIELD_DURATION: Int = 0x05
    }

    object DemandParametersSerializer : KSerializer<DemandParameters> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DemandParameters")

        override fun serialize(encoder: Encoder, value: DemandParameters) {
            val map = CborObject.buildMap {
                value.voltage?.let {
                    put(CborObject.positive(FIELD_VOLTAGE), SerializerUtils.toCborObject(VoltageSerializer, it))
                }
                value.current?.let {
                    put(CborObject.positive(FIELD_CURRENT), SerializerUtils.toCborObject(CurrentSerializer, it))
                }
                value.voltageLimits?.let {
                    put(CborObject.positive(FIELD_VOLTAGE_LIMITS), SerializerUtils.toCborObject(VoltageSerializer, it))
                }
                value.currentLimits?.let {
                    put(CborObject.positive(FIELD_CURRENT_LIMITS), SerializerUtils.toCborObject(CurrentSerializer, it))
                }
                value.powerLimit?.let {
                    put(CborObject.positive(FIELD_POWER_LIMIT), SerializerUtils.toCborObject(PowerSerializer, it))
                }
                value.duration?.let {
                    put(CborObject.positive(FIELD_DURATION), SerializerUtils.toCborObject(DurationSerializer, it))
                }
            }
            encoder.encodeSerializableValue(CborMap.serializer(), map)
        }

        override fun deserialize(decoder: Decoder): DemandParameters {
            val map = decoder.decodeSerializableValue(CborMap.serializer())
            return DemandParameters(
                voltage = map.getByIntKey(FIELD_VOLTAGE)?.let {
                    SerializerUtils.fromCborObject(VoltageSerializer, it)
                },
                current = map.getByIntKey(FIELD_CURRENT)?.let {
                    SerializerUtils.fromCborObject(CurrentSerializer, it)
                },
                voltageLimits = map.getByIntKey(FIELD_VOLTAGE_LIMITS)?.let {
                    SerializerUtils.fromCborObject(VoltageSerializer, it)
                },
                currentLimits = map.getByIntKey(FIELD_CURRENT_LIMITS)?.let {
                    SerializerUtils.fromCborObject(CurrentSerializer, it)
                },
                powerLimit = map.getByIntKey(FIELD_POWER_LIMIT)?.let {
                    SerializerUtils.fromCborObject(PowerSerializer, it)
                },
                duration = map.getByIntKey(FIELD_DURATION)?.let {
                    SerializerUtils.fromCborObject(DurationSerializer, it)
                }
            )
        }
    }
}