package dev.breischl.keneth.core.messages

import dev.breischl.keneth.core.values.Duration
import dev.breischl.keneth.core.values.DurationSerializer
import dev.breischl.keneth.core.values.Energy
import dev.breischl.keneth.core.values.EnergyMix
import dev.breischl.keneth.core.values.EnergyMixSerializer
import dev.breischl.keneth.core.values.EnergySerializer
import dev.breischl.keneth.core.values.Percentage
import dev.breischl.keneth.core.values.PercentageSerializer
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
 * Battery storage status and targets.
 *
 * This message communicates the state of a battery storage system,
 * including current state of charge and charging targets.
 * Defined in EnergyNet Protocol section 4.6.
 *
 * @property soc The current state of charge as a percentage.
 * @property socTarget The target state of charge.
 * @property socTargetTime The time by which the target SOC should be reached.
 * @property capacity The total battery capacity.
 * @property energyMix The energy mix of the stored energy.
 */
data class StorageParameters(
    val soc: Percentage? = null,
    val socTarget: Percentage? = null,
    val socTargetTime: Duration? = null,
    val capacity: Energy? = null,
    val energyMix: EnergyMix? = null
) : Message() {
    override val typeId: UInt = TYPE_ID
    override val payloadSerializer get() = StorageParametersSerializer

    companion object {
        fun serializer() = StorageParametersSerializer
        const val TYPE_ID = 0xDCDC_BA77u
        const val FIELD_SOC: Int = 0x00
        const val FIELD_SOC_TARGET: Int = 0x01
        const val FIELD_SOC_TARGET_TIME: Int = 0x02
        const val FIELD_CAPACITY: Int = 0x03
        const val FIELD_ENERGY_MIX: Int = 0x04
    }


    object StorageParametersSerializer : KSerializer<StorageParameters> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StorageParameters")

        override fun serialize(encoder: Encoder, value: StorageParameters) {
            val map = CborObject.buildMap {
                value.soc?.let {
                    put(CborObject.positive(FIELD_SOC), SerializerUtils.toCborObject(PercentageSerializer, it))
                }
                value.socTarget?.let {
                    put(CborObject.positive(FIELD_SOC_TARGET), SerializerUtils.toCborObject(PercentageSerializer, it))
                }
                value.socTargetTime?.let {
                    put(
                        CborObject.positive(FIELD_SOC_TARGET_TIME),
                        SerializerUtils.toCborObject(DurationSerializer, it)
                    )
                }
                value.capacity?.let {
                    put(CborObject.positive(FIELD_CAPACITY), SerializerUtils.toCborObject(EnergySerializer, it))
                }
                value.energyMix?.let {
                    put(CborObject.positive(FIELD_ENERGY_MIX), SerializerUtils.toCborObject(EnergyMixSerializer, it))
                }
            }
            encoder.encodeSerializableValue(CborMap.serializer(), map)
        }

        override fun deserialize(decoder: Decoder): StorageParameters {
            val map = decoder.decodeSerializableValue(CborMap.serializer())
            return StorageParameters(
                soc = map.getByIntKey(FIELD_SOC)?.let {
                    SerializerUtils.fromCborObject(PercentageSerializer, it)
                },
                socTarget = map.getByIntKey(FIELD_SOC_TARGET)?.let {
                    SerializerUtils.fromCborObject(PercentageSerializer, it)
                },
                socTargetTime = map.getByIntKey(FIELD_SOC_TARGET_TIME)?.let {
                    SerializerUtils.fromCborObject(DurationSerializer, it)
                },
                capacity = map.getByIntKey(FIELD_CAPACITY)?.let {
                    SerializerUtils.fromCborObject(EnergySerializer, it)
                },
                energyMix = map.getByIntKey(FIELD_ENERGY_MIX)?.let {
                    SerializerUtils.fromCborObject(EnergyMixSerializer, it)
                }
            )
        }
    }
}