package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.values.SerializerUtils.asCborMap
import dev.breischl.keneth.core.values.SerializerUtils.getByIntKey
import dev.breischl.keneth.core.values.SerializerUtils.toDoubleValue
import dev.breischl.keneth.core.values.SerializerUtils.toLongValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.orandja.obor.data.CborArray
import net.orandja.obor.data.CborMap
import net.orandja.obor.data.CborNull
import net.orandja.obor.data.CborObject

/**
 * Represents the isolation monitoring state of a DC charging system.
 *
 * DC charging systems must monitor the electrical isolation between the
 * high-voltage DC circuit and the vehicle chassis/ground. This type
 * reports both the overall status and the measured resistance values.
 * Defined in EnergyNet Protocol section 3.1.5.
 *
 * @property state The overall isolation status (OK, WARNING, FAULT, or UNKNOWN).
 * @property positiveResistance The measured isolation resistance on the positive DC rail, if available.
 * @property negativeResistance The measured isolation resistance on the negative DC rail, if available.
 */
@Serializable(with = IsolationStateSerializer::class)
data class IsolationState(
    val state: IsolationStatus,
    val positiveResistance: Resistance?,
    val negativeResistance: Resistance?
) {
    companion object {
        /** CBOR type identifier for IsolationState values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x50
    }
}

/**
 * CBOR serializer for [IsolationState] values.
 *
 * Serializes isolation state as an array within a type wrapper:
 * `{ 0x50: [<state>, {0x15: <negResistance>}, {0x15: <posResistance>}] }`.
 */
object IsolationStateSerializer : KSerializer<IsolationState> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IsolationState")

    override fun serialize(encoder: Encoder, value: IsolationState) {
        val innerArray = CborObject.buildArray {
            add(CborObject.positive(value.state.id.toInt()))
            add(value.negativeResistance?.let {
                SerializerUtils.buildDoubleMap(Resistance.TYPE_ID, it.ohms)
            } ?: CborNull)
            add(value.positiveResistance?.let {
                SerializerUtils.buildDoubleMap(Resistance.TYPE_ID, it.ohms)
            } ?: CborNull)
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(IsolationState.TYPE_ID), innerArray)
        }
        encoder.encodeSerializableValue(CborMap.serializer(), outerMap)
    }

    override fun deserialize(decoder: Decoder): IsolationState {
        val outerMap = decoder.decodeSerializableValue(CborMap.serializer())
        val innerObj = outerMap.getByIntKey(IsolationState.TYPE_ID) ?: error("Missing isolation state value")
        val innerArray = innerObj as? CborArray ?: error("Expected CborArray for IsolationState")
        require(innerArray.size >= 1) { "IsolationState array must have at least 1 element" }

        val stateId = innerArray[0].toLongValue().toInt().toUByte()
        val state = IsolationStatus.fromId(stateId) ?: error("Invalid isolation status: $stateId")

        val negativeResistance = innerArray.getOrNull(1)
            ?.takeIf { it !is CborNull }
            ?.asCborMap()?.getByIntKey(Resistance.TYPE_ID)?.toDoubleValue()
            ?.let { Resistance(it) }

        val positiveResistance = innerArray.getOrNull(2)
            ?.takeIf { it !is CborNull }
            ?.asCborMap()?.getByIntKey(Resistance.TYPE_ID)?.toDoubleValue()
            ?.let { Resistance(it) }

        return IsolationState(state, positiveResistance, negativeResistance)
    }
}
