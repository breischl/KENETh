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
import net.orandja.obor.data.CborObject

/**
 * Represents the mix of energy sources as percentages.
 *
 * This type describes the composition of the current power supply by
 * generation source. For example, a grid might report 40% solar, 35% wind,
 * and 25% hydro. The percentages should sum to 100%.
 * Defined in EnergyNet Protocol section 3.1.3.
 *
 * @property mix A map from [EnergySource] to [Percentage] of the total supply.
 */
@Serializable(with = SourceMixSerializer::class)
data class SourceMix(
    val mix: Map<EnergySource, Percentage>
) {
    companion object {
        /** CBOR type identifier for SourceMix values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x40
    }
}

/**
 * CBOR serializer for [SourceMix] values.
 *
 * Serializes source mix as an array of single-entry maps with value-type wrapping:
 * `{ 0x40: [{sourceId: {0x14: percent}}, ...] }`.
 */
object SourceMixSerializer : KSerializer<SourceMix> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SourceMix")

    override fun serialize(encoder: Encoder, value: SourceMix) {
        val innerArray = CborObject.buildArray {
            for ((source, percentage) in value.mix) {
                add(CborObject.buildMap {
                    put(
                        CborObject.positive(source.id.toInt()),
                        SerializerUtils.buildDoubleMap(Percentage.TYPE_ID, percentage.percent)
                    )
                })
            }
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(SourceMix.TYPE_ID), innerArray)
        }
        encoder.encodeSerializableValue(CborMap.serializer(), outerMap)
    }

    override fun deserialize(decoder: Decoder): SourceMix {
        val outerMap = decoder.decodeSerializableValue(CborMap.serializer())
        val innerObj = outerMap.getByIntKey(SourceMix.TYPE_ID) ?: error("Missing source mix value")
        val innerArray = innerObj as? CborArray ?: error("Expected CborArray for SourceMix entries")

        // TODO: Check the the mix doesn't specify the same source twice
        val mix = innerArray.mapNotNull { entryObj ->
            val entryMap = entryObj.asCborMap()

            // TODO: Should probably be better about handling these missing values - log as parser warnings?
            val entry = entryMap.asMap.entries.firstOrNull() ?: return@mapNotNull null
            val sourceId = entry.key.toLongValue().toInt().toUByte()
            val source = EnergySource.fromId(sourceId) ?: return@mapNotNull null
            val percentValue = entry.value.asCborMap().getByIntKey(Percentage.TYPE_ID)
                ?.toDoubleValue() ?: return@mapNotNull null
            source to Percentage(percentValue)
        }.toMap()
        return SourceMix(mix)
    }
}
