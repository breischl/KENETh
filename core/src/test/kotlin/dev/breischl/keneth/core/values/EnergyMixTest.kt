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
