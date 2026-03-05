package dev.breischl.keneth.core.messages

import dev.breischl.keneth.core.values.Bounds
import dev.breischl.keneth.core.values.BoundsSerializer
import dev.breischl.keneth.core.values.Current
import dev.breischl.keneth.core.values.CurrentSerializer
import dev.breischl.keneth.core.values.IsolationState
import dev.breischl.keneth.core.values.IsolationStateSerializer
import dev.breischl.keneth.core.values.Power
import dev.breischl.keneth.core.values.PowerSerializer
import dev.breischl.keneth.core.values.PriceForecast
import dev.breischl.keneth.core.values.PriceForecastSerializer
import dev.breischl.keneth.core.values.SourceMix
import dev.breischl.keneth.core.values.SourceMixSerializer
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
 * Power supply capabilities and status from the charging equipment.
 *
 * This message is sent by the charger (supply side) to communicate its
 * capabilities, limits, and current operating state.
 * Defined in EnergyNet Protocol section 4.4.
 *
 * @property voltageLimits The voltage range the charger can supply.
 * @property currentLimits The current range the charger can supply.
 * @property powerLimit The maximum power the charger can supply.
 * @property powerMix The current energy source mix.
 * @property energyPrices Forecasted energy prices.
 * @property voltage The current output voltage.
 * @property current The current output current.
 * @property isolation The isolation monitoring state.
 */
data class SupplyParameters(
    val voltageLimits: Bounds<Voltage>? = null,
    val currentLimits: Bounds<Current>? = null,
    val powerLimit: Power? = null,
    val powerMix: SourceMix? = null,
    val energyPrices: PriceForecast? = null,
    val voltage: Voltage? = null,
    val current: Current? = null,
    val isolation: IsolationState? = null
) : Message() {
    override val typeId: UInt = TYPE_ID
    override val payloadSerializer get() = SupplyParametersSerializer

    companion object {
        fun serializer() = SupplyParametersSerializer
        const val TYPE_ID = 0xDCDC_F00Du
        const val FIELD_VOLTAGE_LIMITS: Int = 0x00
        const val FIELD_CURRENT_LIMITS: Int = 0x01
        const val FIELD_POWER_LIMIT: Int = 0x02
        const val FIELD_POWER_MIX: Int = 0x03
        const val FIELD_ENERGY_PRICES: Int = 0x04
        const val FIELD_VOLTAGE: Int = 0x05
        const val FIELD_CURRENT: Int = 0x06
        const val FIELD_ISOLATION: Int = 0x07
    }

    object SupplyParametersSerializer : KSerializer<SupplyParameters> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SupplyParameters")

        override fun serialize(encoder: Encoder, value: SupplyParameters) {
            val map = CborObject.buildMap {
                value.voltageLimits?.let {
                    put(
                        CborObject.positive(FIELD_VOLTAGE_LIMITS),
                        SerializerUtils.toCborObject(BoundsSerializer.voltage, it)
                    )
                }
                value.currentLimits?.let {
                    put(
                        CborObject.positive(FIELD_CURRENT_LIMITS),
                        SerializerUtils.toCborObject(BoundsSerializer.current, it)
                    )
                }
                value.powerLimit?.let {
                    put(CborObject.positive(FIELD_POWER_LIMIT), SerializerUtils.toCborObject(PowerSerializer, it))
                }
                value.powerMix?.let {
                    put(CborObject.positive(FIELD_POWER_MIX), SerializerUtils.toCborObject(SourceMixSerializer, it))
                }
                value.energyPrices?.let {
                    put(
                        CborObject.positive(FIELD_ENERGY_PRICES),
                        SerializerUtils.toCborObject(PriceForecastSerializer, it)
                    )
                }
                value.voltage?.let {
                    put(CborObject.positive(FIELD_VOLTAGE), SerializerUtils.toCborObject(VoltageSerializer, it))
                }
                value.current?.let {
                    put(CborObject.positive(FIELD_CURRENT), SerializerUtils.toCborObject(CurrentSerializer, it))
                }
                value.isolation?.let {
                    put(
                        CborObject.positive(FIELD_ISOLATION),
                        SerializerUtils.toCborObject(IsolationStateSerializer, it)
                    )
                }
            }
            encoder.encodeSerializableValue(CborMap.serializer(), map)
        }

        override fun deserialize(decoder: Decoder): SupplyParameters {
            val map = decoder.decodeSerializableValue(CborMap.serializer())
            return SupplyParameters(
                voltageLimits = map.getByIntKey(FIELD_VOLTAGE_LIMITS)?.let {
                    SerializerUtils.fromCborObject(BoundsSerializer.voltage, it)
                },
                currentLimits = map.getByIntKey(FIELD_CURRENT_LIMITS)?.let {
                    SerializerUtils.fromCborObject(BoundsSerializer.current, it)
                },
                powerLimit = map.getByIntKey(FIELD_POWER_LIMIT)?.let {
                    SerializerUtils.fromCborObject(PowerSerializer, it)
                },
                powerMix = map.getByIntKey(FIELD_POWER_MIX)?.let {
                    SerializerUtils.fromCborObject(SourceMixSerializer, it)
                },
                energyPrices = map.getByIntKey(FIELD_ENERGY_PRICES)?.let {
                    SerializerUtils.fromCborObject(PriceForecastSerializer, it)
                },
                voltage = map.getByIntKey(FIELD_VOLTAGE)?.let {
                    SerializerUtils.fromCborObject(VoltageSerializer, it)
                },
                current = map.getByIntKey(FIELD_CURRENT)?.let {
                    SerializerUtils.fromCborObject(CurrentSerializer, it)
                },
                isolation = map.getByIntKey(FIELD_ISOLATION)?.let {
                    SerializerUtils.fromCborObject(IsolationStateSerializer, it)
                }
            )
        }
    }
}