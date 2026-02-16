package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

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
