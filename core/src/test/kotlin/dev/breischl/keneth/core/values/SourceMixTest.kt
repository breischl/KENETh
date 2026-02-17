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
import kotlin.test.assertTrue

class SourceMixTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `SourceMix round-trip serialization`() {
        val original = SourceMix(
            mapOf(
                EnergySource.SOLAR to Percentage(40.0),
                EnergySource.WIND to Percentage(35.0),
                EnergySource.HYDRO to Percentage(25.0)
            )
        )
        val bytes = cbor.encodeToByteArray(SourceMixSerializer, original)
        val decoded = cbor.decodeFromByteArray(SourceMixSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `SourceMix round-trip serialization - property`() = runBlocking<Unit> {
        val arbSourceMix = arbitrary {
            val size = Arb.int(0..EnergySource.entries.size).bind()
            val sources = EnergySource.entries.shuffled(it.random).take(size)
            val mix = sources.associateWith { Percentage(Arb.double().bind()) }
            SourceMix(mix)
        }
        checkAll(arbSourceMix) { original ->
            val bytes = cbor.encodeToByteArray(SourceMixSerializer, original)
            val decoded = cbor.decodeFromByteArray(SourceMixSerializer, bytes)
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
                        put(CborObject.positive(Percentage.TYPE_ID), CborObject.value(50.0))
                    }
                )
            })
            add(CborObject.buildMap {
                put(
                    CborObject.positive(EnergySource.WIND.id.toInt()),
                    CborObject.buildMap {
                        put(CborObject.positive(Percentage.TYPE_ID), CborObject.value(30.0))
                    }
                )
            })
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(SourceMix.TYPE_ID), innerArray)
        }
        val bytes = cbor.encodeToByteArray(net.orandja.obor.data.CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(SourceMixSerializer, bytes)
        }

        assertEquals(1, decoded.mix.size)
        assertEquals(Percentage(50.0), decoded.mix[EnergySource.WIND])
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
                        put(CborObject.positive(Percentage.TYPE_ID), CborObject.value(70.0))
                    }
                )
            })
            add(CborObject.buildMap {
                put(
                    CborObject.positive(0xFF),
                    CborObject.buildMap {
                        put(CborObject.positive(Percentage.TYPE_ID), CborObject.value(30.0))
                    }
                )
            })
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(SourceMix.TYPE_ID), innerArray)
        }
        val bytes = cbor.encodeToByteArray(net.orandja.obor.data.CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(SourceMixSerializer, bytes)
        }

        assertEquals(1, decoded.mix.size)
        assertEquals(Percentage(70.0), decoded.mix[EnergySource.WIND])
        assertEquals(1, collector.diagnostics.size)
        assertEquals("UNKNOWN_SOURCE_ID", collector.diagnostics[0].code)
    }

    @Test
    fun `deserialize warns on missing percentage`() {
        // Entry with wrong value-type key (Energy TYPE_ID instead of Percentage TYPE_ID)
        val innerArray = CborObject.buildArray {
            add(CborObject.buildMap {
                put(
                    CborObject.positive(EnergySource.WIND.id.toInt()),
                    CborObject.buildMap {
                        put(CborObject.positive(Energy.TYPE_ID), CborObject.value(100.0))
                    }
                )
            })
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(SourceMix.TYPE_ID), innerArray)
        }
        val bytes = cbor.encodeToByteArray(net.orandja.obor.data.CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(SourceMixSerializer, bytes)
        }

        assertTrue(decoded.mix.isEmpty())
        assertEquals(1, collector.diagnostics.size)
        assertEquals("MISSING_PERCENTAGE", collector.diagnostics[0].code)
    }

    @Test
    fun `deserialize warns on empty map entry`() {
        val innerArray = CborObject.buildArray {
            add(CborObject.buildMap { }) // empty map
        }
        val outerMap = CborObject.buildMap {
            put(CborObject.positive(SourceMix.TYPE_ID), innerArray)
        }
        val bytes = cbor.encodeToByteArray(net.orandja.obor.data.CborMap.serializer(), outerMap)

        val collector = DiagnosticCollector()
        val decoded = DiagnosticContext.withCollector(collector) {
            cbor.decodeFromByteArray(SourceMixSerializer, bytes)
        }

        assertTrue(decoded.mix.isEmpty())
        assertEquals(1, collector.diagnostics.size)
        assertEquals("EMPTY_SOURCE_ENTRY", collector.diagnostics[0].code)
    }

    @Test
    fun `Spec example - SourceMix wind 70 percent and local solar 30 percent`() {
        // From spec section 3.1.3:
        // A1 18 40 82 A1 01 A1 14 18 46 A1 09 A1 14 18 1E
        // = {0x40: [{0x01: {0x14: 70}}, {0x09: {0x14: 30}}]}
        val specBytes = "A1184082A101A114184 6A109A114181E".replace(" ", "").hexToByteArray()
        val decoded = cbor.decodeFromByteArray(SourceMixSerializer, specBytes)
        assertEquals(
            SourceMix(
                mapOf(
                    EnergySource.WIND to Percentage(70.0),
                    EnergySource.LOCAL_SOLAR to Percentage(30.0)
                )
            ),
            decoded
        )
    }
}
