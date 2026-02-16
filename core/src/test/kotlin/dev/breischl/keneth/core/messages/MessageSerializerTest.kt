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
import kotlinx.coroutines.runBlocking
import kotlin.time.Instant
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSerializerTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `SessionParameters round-trip serialization with all fields`() {
        val original = SessionParameters(
            identity = "device-001",
            type = "charger",
            version = "1.0.0",
            name = "Test Charger",
            tenant = "tenant-1",
            provider = "provider-1",
            session = "session-123"
        )

        val bytes = cbor.encodeToByteArray(SessionParameters.serializer(), original)
        val decoded = cbor.decodeFromByteArray(SessionParameters.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `SessionParameters round-trip serialization with required fields only`() {
        val original = SessionParameters(identity = "device-002")

        val bytes = cbor.encodeToByteArray(SessionParameters.serializer(), original)
        val decoded = cbor.decodeFromByteArray(SessionParameters.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `DemandParameters round-trip serialization`() {
        val original = DemandParameters(
            voltage = Voltage(400.0),
            current = Current(32.0),
            powerLimit = Power(11000.0),
            duration = Duration(3600000L)
        )

        val bytes = cbor.encodeToByteArray(DemandParameters.serializer(), original)
        val decoded = cbor.decodeFromByteArray(DemandParameters.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `StorageParameters round-trip serialization`() {
        val original = StorageParameters(
            soc = Percentage(75.0),
            socTarget = Percentage(80.0),
            capacity = Energy(60000.0)
        )

        val bytes = cbor.encodeToByteArray(StorageParameters.serializer(), original)
        val decoded = cbor.decodeFromByteArray(StorageParameters.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `SupplyParameters round-trip serialization`() {
        val original = SupplyParameters(
            voltage = Voltage(400.0),
            current = Current(125.0),
            powerLimit = Power(50000.0),
            powerMix = SourceMix(
                mapOf(
                    EnergySource.SOLAR to Percentage(60.0),
                    EnergySource.WIND to Percentage(40.0)
                )
            )
        )

        val bytes = cbor.encodeToByteArray(SupplyParameters.serializer(), original)
        val decoded = cbor.decodeFromByteArray(SupplyParameters.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `SupplyParameters round-trip serialization with all fields`() {
        val original = SupplyParameters(
            voltageLimits = Bounds(Voltage(200.0), Voltage(920.0)),
            currentLimits = Bounds(Current(0.0), Current(500.0)),
            powerLimit = Power(350000.0),
            powerMix = SourceMix(
                mapOf(
                    EnergySource.SOLAR to Percentage(60.0),
                    EnergySource.WIND to Percentage(40.0)
                )
            ),
            energyPrices = PriceForecast(
                entries = listOf(
                    PriceForecastEntry(
                        timestamp = Instant.fromEpochMilliseconds(1700000000000L),
                        amount = Amount(0.25),
                        currency = "EUR"
                    )
                )
            ),
            voltage = Voltage(400.0),
            current = Current(125.0),
            isolation = IsolationState(
                state = IsolationStatus.OK,
                positiveResistance = Resistance(500.0),
                negativeResistance = Resistance(600.0)
            )
        )

        val bytes = cbor.encodeToByteArray(SupplyParameters.serializer(), original)
        val decoded = cbor.decodeFromByteArray(SupplyParameters.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `SoftDisconnect round-trip serialization with both fields`() {
        val original = SoftDisconnect(reconnect = true, reason = "maintenance")

        val bytes = cbor.encodeToByteArray(SoftDisconnect.serializer(), original)
        val decoded = cbor.decodeFromByteArray(SoftDisconnect.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `SoftDisconnect round-trip serialization with only reconnect`() {
        val original = SoftDisconnect(reconnect = false)

        val bytes = cbor.encodeToByteArray(SoftDisconnect.serializer(), original)
        val decoded = cbor.decodeFromByteArray(SoftDisconnect.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `SoftDisconnect round-trip serialization with only reason`() {
        val original = SoftDisconnect(reason = "shutting down")

        val bytes = cbor.encodeToByteArray(SoftDisconnect.serializer(), original)
        val decoded = cbor.decodeFromByteArray(SoftDisconnect.serializer(), bytes)

        assertEquals(original, decoded)
    }

    @Test
    fun `SoftDisconnect round-trip serialization with both null`() {
        val original = SoftDisconnect()

        val bytes = cbor.encodeToByteArray(SoftDisconnect.serializer(), original)
        val decoded = cbor.decodeFromByteArray(SoftDisconnect.serializer(), bytes)

        assertEquals(original, decoded)
    }

    // ==================== Property-Based Tests ====================

    // Use float-derived doubles for message-level tests: OBOR has a buffer sizing bug
    // in CborFloat.cborSize() — it returns 7 for float64 but encodeDouble writes 9 bytes.
    // This is triggered by SerializerUtils.fromCborObject() during deserialization.
    // Using float-sourced doubles avoids the float64 path. Value-type property tests
    // still cover the full Double range via their direct encoding path.
    private val arbSafeDouble = Arb.float().map { it.toDouble() }
    private val arbNullableDouble = arbSafeDouble.orNull(0.2)

    @Test
    fun `SessionParameters round-trip serialization - property`() = runBlocking<Unit> {
        val arbSessionParams = arbitrary {
            SessionParameters(
                identity = Arb.string(1..50).bind(),
                type = Arb.string(0..20).orNull(0.3).bind(),
                version = Arb.string(0..10).orNull(0.3).bind(),
                name = Arb.string(0..30).orNull(0.3).bind(),
                tenant = Arb.string(0..20).orNull(0.3).bind(),
                provider = Arb.string(0..20).orNull(0.3).bind(),
                session = Arb.string(0..30).orNull(0.3).bind()
            )
        }
        checkAll(arbSessionParams) { original ->
            val bytes = cbor.encodeToByteArray(SessionParameters.serializer(), original)
            val decoded = cbor.decodeFromByteArray(SessionParameters.serializer(), bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `SoftDisconnect round-trip serialization - property`() = runBlocking<Unit> {
        val arbSoftDisconnect = arbitrary {
            SoftDisconnect(
                reconnect = Arb.boolean().orNull(0.3).bind(),
                reason = Arb.string(0..50).orNull(0.3).bind()
            )
        }
        checkAll(arbSoftDisconnect) { original ->
            val bytes = cbor.encodeToByteArray(SoftDisconnect.serializer(), original)
            val decoded = cbor.decodeFromByteArray(SoftDisconnect.serializer(), bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `DemandParameters round-trip serialization - property`() = runBlocking<Unit> {
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
    fun `StorageParameters round-trip serialization - property`() = runBlocking<Unit> {
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
    fun `SupplyParameters round-trip serialization - property`() = runBlocking<Unit> {
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

    // ==================== Spec Binary Example Tests ====================

    @Test
    fun `Spec example - SoftDisconnect payload`() {
        // From spec section 4.3.1:
        // A2                        map(2)
        // 00 A1 01 F4               field 0x00: {Flag(0x01): false}
        // 01 A1 00 66 6E6F726D616C  field 0x01: {Text(0x00): "normal"}
        val specBytes = "A200A101F401A100666E6F726D616C".hexToByteArray()
        val decoded = cbor.decodeFromByteArray(SoftDisconnect.serializer(), specBytes)
        assertEquals(SoftDisconnect(reconnect = false, reason = "normal"), decoded)
    }

    @Test
    fun `Message typeIds are correct`() {
        assertEquals(0xFFFF_FFFFu, Ping.typeId)
        assertEquals(0xBABA_5E55u, SessionParameters("").typeId)
        assertEquals(0xBABA_DEADu, SoftDisconnect().typeId)
        assertEquals(0xDCDC_F00Du, SupplyParameters().typeId)
        assertEquals(0xDCDC_FEEDu, DemandParameters().typeId)
        assertEquals(0xDCDC_BA77u, StorageParameters().typeId)
    }
}
