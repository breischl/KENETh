package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.Ping
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.SupplyParameters
import dev.breischl.keneth.transport.MessageTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveTimeoutTest {
    private val serverIdentity = SessionParameters(identity = "test-node", type = "router")
    private val deviceIdentity = SessionParameters(identity = "test-device", type = "charger")

    // ── idle timeout ──────────────────────────────────────────────────────────

    @Test
    fun `session closed after idle timeout with no transfer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val node = EpNode(
            identity = serverIdentity,
            idleReceiveTimeout = 5.seconds,
            coroutineContext = dispatcher,
        )

        val fake = ChannelFakeFrameTransport()
        fake.enqueueMessage(deviceIdentity)
        val session = node.accept(MessageTransport(fake))

        advanceTimeBy(1)
        assertEquals(SessionState.ACTIVE, session.state)

        // Advance past idle timeout — no further messages
        advanceTimeBy(5001)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionState.CLOSED, session.state)
        node.close()
    }

    @Test
    fun `session not closed before idle timeout`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val node = EpNode(
            identity = serverIdentity,
            idleReceiveTimeout = 5.seconds,
            coroutineContext = dispatcher,
        )

        val fake = ChannelFakeFrameTransport()
        fake.enqueueMessage(deviceIdentity)
        val session = node.accept(MessageTransport(fake))

        advanceTimeBy(1)
        assertEquals(SessionState.ACTIVE, session.state)

        // Just before the idle timeout — do not advance to idle; timeout fires at 5001ms
        advanceTimeBy(4999)

        assertEquals(SessionState.ACTIVE, session.state)
        fake.close()
        node.close()
    }

    @Test
    fun `incoming message resets idle timeout`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val node = EpNode(
            identity = serverIdentity,
            idleReceiveTimeout = 5.seconds,
            coroutineContext = dispatcher,
        )

        val fake = ChannelFakeFrameTransport()
        fake.enqueueMessage(deviceIdentity)
        val session = node.accept(MessageTransport(fake))

        advanceTimeBy(1)

        // Advance 4 seconds, then send a Ping — this resets the clock
        advanceTimeBy(4000)
        fake.enqueueMessage(Ping)
        advanceTimeBy(1) // process the Ping so the watchdog resets

        // Advance another 4 seconds — still within 5s of the Ping
        advanceTimeBy(4000)

        assertEquals(SessionState.ACTIVE, session.state)
        fake.close()
        node.close()
    }

    // ── transfer timeout ──────────────────────────────────────────────────────

    @Test
    fun `session closed after transfer timeout with active transfer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val node = EpNode(
            identity = serverIdentity,
            transferReceiveTimeout = 200.milliseconds,
            idleReceiveTimeout = 5.seconds,
            coroutineContext = dispatcher,
        )
        node.addPeer(PeerConfig.Inbound(peerId = "test-device"))

        val fake = ChannelFakeFrameTransport()
        fake.enqueueMessage(deviceIdentity)
        val session = node.accept(MessageTransport(fake))

        advanceTimeBy(1)
        assertEquals(SessionState.ACTIVE, session.state)

        node.startTransfer("test-device", { TransferParams(supply = SupplyParameters()) })
        advanceTimeBy(1)

        // Advance past transfer timeout — no messages from remote
        advanceTimeBy(201)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionState.CLOSED, session.state)
        node.close()
    }

    @Test
    fun `transfer timeout is tighter than idle timeout`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val node = EpNode(
            identity = serverIdentity,
            transferReceiveTimeout = 200.milliseconds,
            idleReceiveTimeout = 5.seconds,
            coroutineContext = dispatcher,
        )
        node.addPeer(PeerConfig.Inbound(peerId = "test-device"))

        val fake = ChannelFakeFrameTransport()
        fake.enqueueMessage(deviceIdentity)
        val session = node.accept(MessageTransport(fake))

        advanceTimeBy(1)

        node.startTransfer("test-device", { TransferParams(supply = SupplyParameters()) })
        advanceTimeBy(1)

        // 300ms passes — transfer timeout fires, not the idle timeout
        advanceTimeBy(300)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionState.CLOSED, session.state)
        node.close()
    }
}
