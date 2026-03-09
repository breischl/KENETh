package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.DemandParameters
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.SupplyParameters
import dev.breischl.keneth.core.values.Current
import dev.breischl.keneth.core.values.Voltage
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class EnergyTransferTest {
    private val serverIdentity = SessionParameters(identity = "test-node", type = "router")
    private val deviceIdentity = SessionParameters(identity = "test-device", type = "charger")

    /** Records NodeListener events. */
    private class RecordingNodeListener : NodeListener {
        val events = mutableListOf<String>()

        override fun onPeerConnected(session: SessionSnapshot) {
            events.add("connected:${session.peerId}")
        }

        override fun onPeerDisconnected(session: SessionSnapshot) {
            events.add("disconnected:${session.peerId}")
        }

        override fun onSessionError(session: SessionSnapshot, error: Throwable) {
            events.add("error:${error.message}")
        }
    }

    /**
     * Creates an EpNode with a connected peer, ready for transfer testing.
     * Advances the scheduler to process the handshake.
     * Returns (node, fakeTransport, listener).
     */
    private suspend fun kotlinx.coroutines.test.TestScope.createNodeWithConnectedPeer(
        testDispatcher: kotlinx.coroutines.test.TestDispatcher,
    ): Triple<EpNode, ChannelFakeFrameTransport, RecordingNodeListener> {
        val listener = RecordingNodeListener()
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = listener,
            coroutineContext = testDispatcher,
        )

        node.addPeer(
            PeerConfig.Inbound(
                peerId = "charger-1",
                expectedIdentity = "test-device"
            )
        )

        val fake = ChannelFakeFrameTransport()
        fake.enqueue(frameResultFor(deviceIdentity))
        val transport = MessageTransport(fake)
        node.accept(transport)

        // Advance 1ms to process the handshake without advancing to the idle timeout
        testScheduler.advanceTimeBy(1)

        check(node.peers["charger-1"]?.isConnected == true) {
            "Peer should be connected after handshake. isConnected: ${node.peers["charger-1"]?.isConnected}"
        }

        return Triple(node, fake, listener)
    }

    /** Unwraps a [StartTransferResult.Success], failing the test on any other result. */
    private fun StartTransferResult.requireSuccess(): EnergyTransfer {
        return when (this) {
            is StartTransferResult.Success -> transfer
            else -> fail("Expected Success but got $this")
        }
    }

    private fun countSentMessagesByType(fake: ChannelFakeFrameTransport, typeId: UInt): Int {
        return fake.sentFrames.count { it.messageTypeId == typeId }
    }

    // -- Tests --

    @Test
    fun `start transfer sends parameters at tick rate`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, fake, _) = createNodeWithConnectedPeer(dispatcher)

        val supply = SupplyParameters(voltage = Voltage(400.0), current = Current(32.0))
        val demand = DemandParameters(voltage = Voltage(400.0))
        val params = TransferParams(supply = supply, demand = demand)

        node.startTransfer("charger-1", { params }, tickRate = 100.milliseconds).requireSuccess()

        // After first tick (immediate send before delay)
        advanceTimeBy(1)
        val supplyCount1 = countSentMessagesByType(fake, SupplyParameters.TYPE_ID)
        val demandCount1 = countSentMessagesByType(fake, DemandParameters.TYPE_ID)
        assertEquals(1, supplyCount1, "Expected 1 supply message after first tick")
        assertEquals(1, demandCount1, "Expected 1 demand message after first tick")

        // After second tick
        advanceTimeBy(100)
        val supplyCount2 = countSentMessagesByType(fake, SupplyParameters.TYPE_ID)
        assertEquals(2, supplyCount2, "Expected 2 supply messages after second tick")

        node.close()
    }

    @Test
    fun `callback result is used on each tick`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, fake, _) = createNodeWithConnectedPeer(dispatcher)

        var params = TransferParams(supply = SupplyParameters(voltage = Voltage(400.0)))
        node.startTransfer("charger-1", { params }, tickRate = 100.milliseconds).requireSuccess()

        // First tick sends supply only
        advanceTimeBy(1)
        assertEquals(1, countSentMessagesByType(fake, SupplyParameters.TYPE_ID))
        assertEquals(0, countSentMessagesByType(fake, DemandParameters.TYPE_ID))

        // Update the captured state — next tick should pick it up
        params = TransferParams(
            supply = SupplyParameters(voltage = Voltage(800.0)),
            demand = DemandParameters(voltage = Voltage(800.0)),
        )

        // Second tick should use updated params
        advanceTimeBy(100)
        assertTrue(
            countSentMessagesByType(fake, DemandParameters.TYPE_ID) >= 1,
            "Expected demand messages after callback state update"
        )

        node.close()
    }

    @Test
    fun `stop transfer stops publishing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, fake, _) = createNodeWithConnectedPeer(dispatcher)

        val supply = SupplyParameters(voltage = Voltage(400.0))
        val transfer = node.startTransfer(
            "charger-1", { TransferParams(supply = supply) }, tickRate = 100.milliseconds
        ).requireSuccess()

        // First tick
        advanceTimeBy(1)
        val countBefore = countSentMessagesByType(fake, SupplyParameters.TYPE_ID)

        // Stop
        node.stopTransfer("charger-1")
        advanceTimeBy(1) // let cancellation propagate

        // Advance more time — no new messages
        advanceTimeBy(500)
        val countAfter = countSentMessagesByType(fake, SupplyParameters.TYPE_ID)
        assertEquals(countBefore, countAfter, "No new messages should be sent after stop")
        assertEquals(TransferState.STOPPED, transfer.state)

        node.close()
    }

    @Test
    fun `transfer sends on every tick`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, fake, _) = createNodeWithConnectedPeer(dispatcher)

        var params = TransferParams(supply = SupplyParameters(voltage = Voltage(200.0)))
        node.startTransfer("charger-1", { params }, tickRate = 50.milliseconds).requireSuccess()

        // First tick
        advanceTimeBy(1)

        // Advance through several ticks, updating captured state between them
        params = TransferParams(supply = SupplyParameters(voltage = Voltage(400.0)))
        advanceTimeBy(50)
        params = TransferParams(supply = SupplyParameters(voltage = Voltage(600.0)))
        advanceTimeBy(50)
        params = TransferParams(supply = SupplyParameters(voltage = Voltage(800.0)))
        advanceTimeBy(50)

        // All ticks should have sent supply messages
        assertTrue(
            countSentMessagesByType(fake, SupplyParameters.TYPE_ID) >= 4,
            "Expected at least 4 supply messages"
        )

        node.close()
    }

    @Test
    fun `peer disconnect stops transfer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, fake, _) = createNodeWithConnectedPeer(dispatcher)

        val transfer = node.startTransfer(
            "charger-1",
            { TransferParams(supply = SupplyParameters(voltage = Voltage(400.0))) },
            tickRate = 100.milliseconds,
        ).requireSuccess()

        // First tick
        advanceTimeBy(1)
        assertEquals(TransferState.ACTIVE, transfer.state)

        // Close the transport — simulates peer disconnect
        fake.close()
        advanceTimeBy(200) // let the transfer loop detect disconnection

        assertEquals(TransferState.STOPPED, transfer.state)

        node.close()
    }

    @Test
    fun `start transfer on disconnected peer returns PeerNotConnected`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val listener = RecordingNodeListener()
        val node = EpNode(
            identity = serverIdentity,
            nodeListener = listener,
            coroutineContext = dispatcher,
        )

        node.addPeer(PeerConfig.Inbound(peerId = "charger-1"))

        val result = node.startTransfer("charger-1", { TransferParams(supply = SupplyParameters()) })
        assertIs<StartTransferResult.PeerNotConnected>(result)
        assertEquals("charger-1", result.peerId)

        node.close()
    }

    @Test
    fun `start transfer on unknown peer returns PeerNotFound`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val node = EpNode(
            identity = serverIdentity,
            coroutineContext = dispatcher,
        )

        val result = node.startTransfer("nonexistent", { TransferParams(supply = SupplyParameters()) })
        assertIs<StartTransferResult.PeerNotFound>(result)
        assertEquals("nonexistent", result.peerId)

        node.close()
    }

    @Test
    fun `start transfer when already active returns TransferAlreadyActive`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, _, _) = createNodeWithConnectedPeer(dispatcher)

        node.startTransfer("charger-1", { TransferParams(supply = SupplyParameters()) }, tickRate = 100.milliseconds)
            .requireSuccess()

        val result = node.startTransfer("charger-1", { TransferParams(supply = SupplyParameters()) })
        assertIs<StartTransferResult.TransferAlreadyActive>(result)
        assertEquals("charger-1", result.peerId)

        node.close()
    }

}
