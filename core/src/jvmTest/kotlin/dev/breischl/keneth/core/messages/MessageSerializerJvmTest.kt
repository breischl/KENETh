package dev.breischl.keneth.core.messages

import dev.breischl.keneth.core.values.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlin.time.Instant
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * JVM-only property-based tests for EP message serialization.
 *
 * These tests use float-sourced doubles to work around an OBOR WriterException bug:
 * `CborFloat.cborSize()` returns 7 for float64 but `encodeDouble` writes 9 bytes.
 * On JVM, the encoder picks float32 for these values, avoiding the bug. On JS it
 * always picks float64 so the workaround fails — keeping these tests JVM-only.
 *
 * All other message serialization tests live in [MessageSerializerTest] (commonTest).
 */
class MessageSerializerJvmTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    // Use float-derived doubles: OBOR has a buffer sizing bug in CborFloat.cborSize()
    // — it returns 7 for float64 but encodeDouble writes 9 bytes. On JVM, the encoder
    // picks float32 for these values, avoiding the bug. On JS it always picks float64.
    private val arbSafeDouble = Arb.float().map { it.toDouble() }
    private val arbNullableDouble = arbSafeDouble.orNull(0.2)

    @Test
    fun `DemandParameters round-trip serialization - property`() = runTest {
        val arbDemandParams = arbitrary {
            DemandParameters(
                voltage = arbNullableDouble.bind()?.let { Voltage(it) },
                current = arbNullableDouble.bind()?.let { Current(it) },
                voltageLimits = arbNullableDouble.bind()?.let { Voltage(it) },
                currentLimits = arbNullableDouble.bind()?.let { Current(it) },
                powerLimit = arbNullableDouble.bind()?.let { Power(it) },
                duration = Arb.long().orNull(0.2).bind()?.let { Duration(it) }
            )
        }
        checkAll(arbDemandParams) { original ->
            val bytes = cbor.encodeToByteArray(DemandParameters.serializer(), original)
            val decoded = cbor.decodeFromByteArray(DemandParameters.serializer(), bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `StorageParameters round-trip serialization - property`() = runTest {
        val arbStorageParams = arbitrary {
            StorageParameters(
                soc = arbNullableDouble.bind()?.let { Percentage(it) },
                socTarget = arbNullableDouble.bind()?.let { Percentage(it) },
                socTargetTime = Arb.long().orNull(0.2).bind()?.let { Duration(it) },
                capacity = arbNullableDouble.bind()?.let { Energy(it) },
                energyMix = Arb.boolean().bind().let { hasEnergyMix ->
                    if (!hasEnergyMix) null
                    else {
                        val size = Arb.int(0..EnergySource.entries.size).bind()
                        val sources = EnergySource.entries.shuffled(it.random).take(size)
                        EnergyMix(sources.associateWith { Energy(arbSafeDouble.bind()) })
                    }
                }
            )
        }
        checkAll(arbStorageParams) { original ->
            val bytes = cbor.encodeToByteArray(StorageParameters.serializer(), original)
            val decoded = cbor.decodeFromByteArray(StorageParameters.serializer(), bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `SupplyParameters round-trip serialization - property`() = runTest {
        val arbSupplyParams = arbitrary {
            val hasVoltageLimits = Arb.boolean().bind()
            val hasCurrentLimits = Arb.boolean().bind()
            val hasPowerMix = Arb.boolean().bind()
            val hasPrices = Arb.boolean().bind()
            val hasIsolation = Arb.boolean().bind()

            SupplyParameters(
                voltageLimits = if (!hasVoltageLimits) null else
                    Bounds(Voltage(arbSafeDouble.bind()), Voltage(arbSafeDouble.bind())),
                currentLimits = if (!hasCurrentLimits) null else
                    Bounds(Current(arbSafeDouble.bind()), Current(arbSafeDouble.bind())),
                powerLimit = arbNullableDouble.bind()?.let { Power(it) },
                powerMix = if (!hasPowerMix) null else {
                    val size = Arb.int(0..EnergySource.entries.size).bind()
                    val sources = EnergySource.entries.shuffled(it.random).take(size)
                    SourceMix(sources.associateWith { Percentage(arbSafeDouble.bind()) })
                },
                energyPrices = if (!hasPrices) null else {
                    val numEntries = Arb.int(0..5).bind()
                    PriceForecast(List(numEntries) {
                        val millis = Arb.long(-62135596800000L..253402300799999L).bind()
                        PriceForecastEntry(
                            Instant.fromEpochMilliseconds(millis),
                            Amount(arbSafeDouble.bind()),
                            Arb.string(0..5).bind()
                        )
                    })
                },
                voltage = arbNullableDouble.bind()?.let { Voltage(it) },
                current = arbNullableDouble.bind()?.let { Current(it) },
                isolation = if (!hasIsolation) null else {
                    IsolationState(
                        state = Arb.enum<IsolationStatus>().bind(),
                        positiveResistance = arbNullableDouble.bind()?.let { Resistance(it) },
                        negativeResistance = arbNullableDouble.bind()?.let { Resistance(it) }
                    )
                }
            )
        }
        checkAll(arbSupplyParams) { original ->
            val bytes = cbor.encodeToByteArray(SupplyParameters.serializer(), original)
            val decoded = cbor.decodeFromByteArray(SupplyParameters.serializer(), bytes)
            assertEquals(original, decoded)
        }
    }
}
