package dev.breischl.keneth.core.values

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import net.orandja.obor.codec.Cbor
import kotlin.test.Test
import kotlin.test.assertEquals

class IsolationStateTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    @Test
    fun `IsolationState round-trip serialization with all fields`() {
        val original = IsolationState(
            state = IsolationStatus.OK,
            positiveResistance = Resistance(500.0),
            negativeResistance = Resistance(600.0)
        )
        val bytes = cbor.encodeToByteArray(IsolationStateSerializer, original)
        val decoded = cbor.decodeFromByteArray(IsolationStateSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `IsolationState round-trip serialization with null resistances`() {
        val original = IsolationState(
            state = IsolationStatus.UNKNOWN,
            positiveResistance = null,
            negativeResistance = null
        )
        val bytes = cbor.encodeToByteArray(IsolationStateSerializer, original)
        val decoded = cbor.decodeFromByteArray(IsolationStateSerializer, bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun `IsolationState round-trip serialization - property`() = runBlocking<Unit> {
        val arbIsolationState = arbitrary {
            val state = Arb.enum<IsolationStatus>().bind()
            val hasPositive = Arb.boolean().bind()
            val hasNegative = Arb.boolean().bind()
            val positiveResistance = if (hasPositive) Resistance(Arb.double().bind()) else null
            val negativeResistance = if (hasNegative) Resistance(Arb.double().bind()) else null
            IsolationState(state, positiveResistance, negativeResistance)
        }
        checkAll(arbIsolationState) { original ->
            val bytes = cbor.encodeToByteArray(IsolationStateSerializer, original)
            val decoded = cbor.decodeFromByteArray(IsolationStateSerializer, bytes)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `Spec example - IsolationState ok with 1k ohm resistances`() {
        // From spec section 3.1.5:
        // A1 18 50 83 01 A1 15 19 03 E8 A1 15 19 03 E8
        // = {0x50: [1, {0x15: 1000}, {0x15: 1000}]}
        val specBytes = "A118508301A11519 03E8A1151903E8".replace(" ", "").hexToByteArray()
        val decoded = cbor.decodeFromByteArray(IsolationStateSerializer, specBytes)
        assertEquals(
            IsolationState(
                state = IsolationStatus.OK,
                positiveResistance = Resistance(1000.0),
                negativeResistance = Resistance(1000.0)
            ),
            decoded
        )
    }
}
