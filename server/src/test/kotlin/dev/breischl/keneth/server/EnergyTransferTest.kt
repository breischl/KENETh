package dev.breischl.keneth.server

import dev.breischl.keneth.core.frames.Frame
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.parsing.ParseResult
import dev.breischl.keneth.core.values.*
import dev.breischl.keneth.transport.FrameTransport
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class EnergyTransferTest {
    private val cbor = Cbor { ingnoreUnknownKeys = true }

    private val serverIdentity = SessionParameters(identity = "test-node", type = "router")
    private val deviceIdentity = SessionParameters(identity = "test-device", type = "charger")

    private class ChannelFakeFrameTransport(
        private val channel: Channel<ParseResult<Frame>> = Channel(Channel.UNLIMITED)
    ) : FrameTransport {
        val sentFrames = mutableListOf<Frame>()
        var closed = false

        override suspend fun send(frame: Frame) {
            sentFrames.add(frame)
        }

        override fun receive(): Flow<ParseResult<Frame>> = channel.consumeAsFlow()

        override fun close() {
            closed = true
            channel.close()
        }

        suspend fun enqueue(frame: ParseResult<Frame>) {
            channel.send(frame)
        }
    }

    private fun encodeMessage(message: Message): ByteArray {
        @Suppress("UNCHECKED_CAST")
        return cbor.encodeToByteArray(
            message.payloadSerializer as KSerializer<Message>,
            message
        )
    }

    private fun frameResultFor(message: Message): ParseResult<Frame> {
        val payload = encodeMessage(message)
        return ParseResult.success(
            Frame(emptyMap(), message.typeId, payload),
            emptyList()
        )
    }

    /** Records NodeListener events. */
    private class RecordingNodeListener : NodeListener {
        val events = mutableListOf<String>()

        override fun onPeerConnected(peer: PeerSnapshot) {
            events.add("connected:${peer.peerId}")
        }

        override fun onPeerDisconnected(peer: PeerSnapshot) {
            events.add("disconnected:${peer.peerId}")
        }

        override fun onTransferStarted(transfer: EnergyTransferSnapshot) {
            events.add("transfer-started:${transfer.peerId}")
        }

        override fun onTransferStopped(transfer: EnergyTransferSnapshot) {
            events.add("transfer-stopped:${transfer.peerId}")
        }

        override fun onError(error: Throwable) {
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
            config = NodeConfig(identity = serverIdentity),
            listener = listener,
            coroutineContext = testDispatcher,
        )

        node.addPeer(
            PeerConfig(
                peerId = "charger-1",
                direction = PeerDirection.INBOUND,
                expectedIdentity = "test-device"
            )
        )

        val fake = ChannelFakeFrameTransport()
        fake.enqueue(frameResultFor(deviceIdentity))
        val transport = MessageTransport(fake)
        node.server.accept(transport)

        // Advance scheduler to process the handshake and connect the peer
        testScheduler.advanceUntilIdle()

        check(node.peers["charger-1"]?.connectionState == ConnectionState.CONNECTED) {
            "Peer should be connected after handshake. State: ${node.peers["charger-1"]?.connectionState}"
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

        node.startTransfer("charger-1", params, tickRate = 100.milliseconds).requireSuccess()

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
    fun `update transfer changes parameters on next tick`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, fake, _) = createNodeWithConnectedPeer(dispatcher)

        val supply1 = SupplyParameters(voltage = Voltage(400.0))
        node.startTransfer("charger-1", TransferParams(supply = supply1), tickRate = 100.milliseconds).requireSuccess()

        // First tick sends supply1
        advanceTimeBy(1)
        assertEquals(1, countSentMessagesByType(fake, SupplyParameters.TYPE_ID))

        // Update to include demand as well
        val supply2 = SupplyParameters(voltage = Voltage(800.0))
        val demand = DemandParameters(voltage = Voltage(800.0))
        node.updateTransfer("charger-1", TransferParams(supply = supply2, demand = demand))

        // Second tick should use updated params
        advanceTimeBy(100)
        assertTrue(
            countSentMessagesByType(fake, DemandParameters.TYPE_ID) >= 1,
            "Expected demand messages after update"
        )

        node.close()
    }

    @Test
    fun `stop transfer stops publishing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, fake, listener) = createNodeWithConnectedPeer(dispatcher)

        val supply = SupplyParameters(voltage = Voltage(400.0))
        val transfer = node.startTransfer(
            "charger-1", TransferParams(supply = supply), tickRate = 100.milliseconds
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
        assertContains(listener.events, "transfer-stopped:charger-1")

        node.close()
    }

    @Test
    fun `transfer survives multiple parameter updates`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, fake, _) = createNodeWithConnectedPeer(dispatcher)

        node.startTransfer(
            "charger-1",
            TransferParams(supply = SupplyParameters(voltage = Voltage(200.0))),
            tickRate = 50.milliseconds
        ).requireSuccess()

        // First tick
        advanceTimeBy(1)

        // Update several times
        node.updateTransfer("charger-1", TransferParams(supply = SupplyParameters(voltage = Voltage(400.0))))
        advanceTimeBy(50)
        node.updateTransfer("charger-1", TransferParams(supply = SupplyParameters(voltage = Voltage(600.0))))
        advanceTimeBy(50)
        node.updateTransfer("charger-1", TransferParams(supply = SupplyParameters(voltage = Voltage(800.0))))
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
        val (node, fake, listener) = createNodeWithConnectedPeer(dispatcher)

        val transfer = node.startTransfer(
            "charger-1",
            TransferParams(supply = SupplyParameters(voltage = Voltage(400.0))),
            tickRate = 100.milliseconds,
        ).requireSuccess()

        // First tick
        advanceTimeBy(1)
        assertEquals(TransferState.ACTIVE, transfer.state)

        // Close the transport — simulates peer disconnect
        fake.close()
        advanceTimeBy(200) // let the transfer loop detect disconnection

        assertEquals(TransferState.STOPPED, transfer.state)
        assertContains(listener.events, "transfer-stopped:charger-1")

        node.close()
    }

    @Test
    fun `start transfer on disconnected peer returns PeerNotConnected`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val listener = RecordingNodeListener()
        val node = EpNode(
            config = NodeConfig(identity = serverIdentity),
            listener = listener,
            coroutineContext = dispatcher,
        )

        node.addPeer(PeerConfig(peerId = "charger-1", direction = PeerDirection.INBOUND))

        val result = node.startTransfer("charger-1", TransferParams(supply = SupplyParameters()))
        assertIs<StartTransferResult.PeerNotConnected>(result)
        assertEquals("charger-1", result.peerId)
        assertEquals(ConnectionState.DISCONNECTED, result.state)

        node.close()
    }

    @Test
    fun `start transfer on unknown peer returns PeerNotFound`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val node = EpNode(
            config = NodeConfig(identity = serverIdentity),
            coroutineContext = dispatcher,
        )

        val result = node.startTransfer("nonexistent", TransferParams(supply = SupplyParameters()))
        assertIs<StartTransferResult.PeerNotFound>(result)
        assertEquals("nonexistent", result.peerId)

        node.close()
    }

    @Test
    fun `start transfer when already active returns TransferAlreadyActive`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, _, _) = createNodeWithConnectedPeer(dispatcher)

        node.startTransfer("charger-1", TransferParams(supply = SupplyParameters()), tickRate = 100.milliseconds)
            .requireSuccess()

        val result = node.startTransfer("charger-1", TransferParams(supply = SupplyParameters()))
        assertIs<StartTransferResult.TransferAlreadyActive>(result)
        assertEquals("charger-1", result.peerId)

        node.close()
    }

    @Test
    fun `listener callbacks fire for transfer lifecycle`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (node, _, listener) = createNodeWithConnectedPeer(dispatcher)

        node.startTransfer("charger-1", TransferParams(supply = SupplyParameters()), tickRate = 100.milliseconds)
            .requireSuccess()
        assertContains(listener.events, "transfer-started:charger-1")

        // Let the transfer coroutine start executing
        advanceTimeBy(1)

        node.stopTransfer("charger-1")
        testScheduler.advanceUntilIdle() // let cancellation propagate

        assertContains(listener.events, "transfer-stopped:charger-1")

        node.close()
    }
}
