package dev.breischl.keneth.core.values

import dev.breischl.keneth.core.diagnostics.DiagnosticCollector
import dev.breischl.keneth.core.diagnostics.DiagnosticContext
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import net.orandja.obor.data.CborObject
import kotlin.test.Test
import kotlin.test.assertEquals

class EnergyMixTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `EnergyMix round-trip serialization`() {
        val original = EnergyMix(
            mapOf(
                EnergySource.SOLAR to Energy(1000.0),
                EnergySource.WIND to Energy(2000.0)
            )
        )
        val bytes = cbor.encodeToByteArray(EnergyMixSerializer, original)
        val decoded = cbor.decodeFromByteArray(EnergyMixSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `EnergyMix round-trip serialization - property`() = runBlocking<Unit> {
        val arbEnergyMix = arbitrary {
            val size = Arb.int(0..EnergySource.entries.size).bind()
            val sources = EnergySource.entries.shuffled(it.random).take(size)
            val mix = sources.associateWith { Energy(Arb.double().bind()) }
            EnergyMix(mix)
        }
        checkAll(arbEnergyMix) { original ->
            val bytes = cbor.encodeToByteArray(EnergyMixSerializer, original)
            val decoded = cbor.decodeFromByteArray(EnergyMixSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `deserialize warns on duplicate source and keeps first`() {
        val innerArray = CborObject.buildArray {
            add(CborObject.buildMap {
                put(
                    CborObject.positive(EnergySource.WIND.id.toInt()),
                    CborObject.buildMap {
                        put(CborObject.positive(Energy.TYPE_ID), CborObject.value(1000.0))
                    }
                )
            })
            add(CborObject.buildMap {
                put(
                    CborObject.positive(EnergySource.WIND.id.toInt()),
                    CborObject.buildMap {
                        put(CborObject.positive(Energy.TYPE_ID), CborObject.value(2000.0))
                    }
                )
            })
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(EnergyMix.TYPE_ID), innerArray)
        }
        val bytes = cbor.encodeToByteArray(net.orandja.obor.data.CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(EnergyMixSerializer, bytes)
        }

        assertEquals(1, decoded.mix.size)
        assertEquals(Energy(1000.0), decoded.mix[EnergySource.WIND])
        assertEquals(1, collector.diagnostics.size)
        assertEquals("DUPLICATE_SOURCE", collector.diagnostics[0].code)
    }

    @Test
    fun `deserialize skips unknown source IDs with warning`() {
        val innerArray = CborObject.buildArray {
            add(CborObject.buildMap {
                put(
                    CborObject.positive(EnergySource.WIND.id.toInt()),
                    CborObject.buildMap {
                        put(CborObject.positive(Energy.TYPE_ID), CborObject.value(500.0))
                    }
                )
            })
            add(CborObject.buildMap {
                put(
                    CborObject.positive(0xFF),
                    CborObject.buildMap {
                        put(CborObject.positive(Energy.TYPE_ID), CborObject.value(300.0))
                    }
                )
            })
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(EnergyMix.TYPE_ID), innerArray)
        }
        val bytes = cbor.encodeToByteArray(net.orandja.obor.data.CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(EnergyMixSerializer, bytes)
        }

        assertEquals(1, decoded.mix.size)
        assertEquals(Energy(500.0), decoded.mix[EnergySource.WIND])
        assertEquals(1, collector.diagnostics.size)
        assertEquals("UNKNOWN_SOURCE_ID", collector.diagnostics[0].code)
    }

    @Test
    fun `Spec example - EnergyMix wind hydro and local solar`() {
        // From spec section 3.1.4:
        // A1 18 41 83 A1 01 A1 13 19 18 38 A1 03 A1 13 19 CB 20 A1 09 A1 13 19 09 56
        // = {0x41: [{0x01: {0x13: 6200}}, {0x03: {0x13: 52000}}, {0x09: {0x13: 2390}}]}
        val specBytes = "A1184183A101A11319183 8A103A1131 9CB20A109A113190956".replace(" ", "").hexToByteArray()
        val decoded = cbor.decodeFromByteArray(EnergyMixSerializer, specBytes)
        assertEquals(
            EnergyMix(
                mapOf(
                    EnergySource.WIND to Energy(6200.0),
                    EnergySource.HYDRO to Energy(52000.0),
                    EnergySource.LOCAL_SOLAR to Energy(2390.0)
                )
            ),
            decoded
        )
    }
}
