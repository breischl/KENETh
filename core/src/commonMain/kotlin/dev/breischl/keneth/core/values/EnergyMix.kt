package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.diagnostics.DiagnosticContext
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
 * Represents the mix of energy sources as absolute energy values.
 *
 * Unlike [SourceMix] which uses percentages, this type describes the
 * absolute energy contribution from each source in watt-hours. This is
 * useful for reporting total energy consumed from each generation type.
 * Defined in EnergyNet Protocol section 3.1.4.
 *
 * @property mix A map from [EnergySource] to [Energy] (in watt-hours).
 */
@Serializable(with = EnergyMixSerializer::class)
data class EnergyMix(
    val mix: Map<EnergySource, Energy>
) {
    companion object {
        /** CBOR type identifier for EnergyMix values in the EnergyNet Protocol. */
        const val TYPE_ID: Int = 0x41
    }
}

/**
 * CBOR serializer for [EnergyMix] values.
 *
 * Serializes energy mix as an array of single-entry maps with value-type wrapping:
 * `{ 0x41: [{sourceId: {0x13: wattHours}}, ...] }`.
 */
object EnergyMixSerializer : KSerializer<EnergyMix> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EnergyMix")

    override fun serialize(encoder: Encoder, value: EnergyMix) {
        val innerArray = CborObject.buildArray {
            for ((source, energy) in value.mix) {
                add(CborObject.buildMap {
                    put(
                        CborObject.positive(source.id.toInt()),
                        SerializerUtils.buildDoubleMap(Energy.TYPE_ID, energy.wattHours)
                    )
                })
            }
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(EnergyMix.TYPE_ID), innerArray)
        }
        encoder.encodeSerializableValue(CborMap.serializer(), outerMap)
    }

    override fun deserialize(decoder: Decoder): EnergyMix {
        val outerMap = decoder.decodeSerializableValue(CborMap.serializer())
        val innerObj = outerMap.getByIntKey(EnergyMix.TYPE_ID) ?: error("Missing energy mix value")
        val innerArray = innerObj as? CborArray ?: error("Expected CborArray for EnergyMix entries")
        val collector = DiagnosticContext.get()
        val mix = mutableMapOf<EnergySource, Energy>()
        for (entryObj in innerArray) {
            val entryMap = entryObj.asCborMap()
            val entry = entryMap.asMap.entries.firstOrNull()
            if (entry == null) {
                collector?.warning("EMPTY_SOURCE_ENTRY", "Empty map in EnergyMix entry, skipping")
                continue
            }
            val sourceId = entry.key.toLongValue().toInt().toUByte()
            val source = EnergySource.fromId(sourceId)
            if (source == null) {
                collector?.warning(
                    "UNKNOWN_SOURCE_ID",
                    "Unknown source ID 0x${sourceId.toString(16)} in EnergyMix, skipping"
                )
                continue
            }
            val wattHours = entry.value.asCborMap().getByIntKey(Energy.TYPE_ID)
                ?.toDoubleValue()
            if (wattHours == null) {
                collector?.warning("MISSING_ENERGY", "Missing energy value for source ${source.name}, skipping")
                continue
            }
            if (source in mix) {
                collector?.warning("DUPLICATE_SOURCE", "Duplicate source in EnergyMix: ${source.name}, keeping first")
                continue
            }
            mix[source] = Energy(wattHours)
        }
        return EnergyMix(mix)
    }
}
