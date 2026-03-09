package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.SessionParameters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryInboundAcceptorTest {

    private val identityA = SessionParameters(identity = "node-a", type = "router")
    private val identityB = SessionParameters(identity = "node-b", type = "router")

    private class RecordingNodeListener : NodeListener {
        val connectedPeers = mutableListOf<SessionSnapshot>()
        val disconnectedPeers = mutableListOf<SessionSnapshot>()

        override fun onPeerConnected(session: SessionSnapshot) {
            connectedPeers.add(session)
        }

        override fun onPeerDisconnected(session: SessionSnapshot) {
            disconnectedPeers.add(session)
        }
    }

    @Test
    fun `connect queues transport and start drains it into node accept`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val acceptor = InMemoryInboundAcceptor(dispatcher)

        val node = EpNode(
            identity = identityA,
            coroutineContext = dispatcher,
        )

        // start before connect — channel is empty, loop suspends
        acceptor.start(node)
        assertEquals(0, node.sessions.size)

        // connect enqueues one transport; drain loop should pick it up immediately
        acceptor.connect(listener = null)
        assertEquals(1, node.sessions.size)

        acceptor.close()
        node.close()
    }

    @Test
    fun `multiple connects are all accepted`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val acceptor = InMemoryInboundAcceptor(dispatcher)
        val node = EpNode(identity = identityA, coroutineContext = dispatcher)

        acceptor.start(node)

        repeat(3) { acceptor.connect(listener = null) }

        assertEquals(3, node.sessions.size)

        acceptor.close()
        node.close()
    }

    @Test
    fun `connect before start - transports are buffered and accepted on start`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val acceptor = InMemoryInboundAcceptor(dispatcher)
        val node = EpNode(identity = identityA, coroutineContext = dispatcher)

        // connect first, start second
        repeat(2) { acceptor.connect(listener = null) }
        assertEquals(0, node.sessions.size)

        acceptor.start(node)
        assertEquals(2, node.sessions.size)

        acceptor.close()
        node.close()
    }

    @Test
    fun `two EpNodes wire together via InMemoryInboundAcceptor and both onPeerConnected fire`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val acceptor = InMemoryInboundAcceptor(dispatcher)

        val listenerA = RecordingNodeListener()
        val nodeA = EpNode(
            identity = identityA,
            acceptor = acceptor,
            nodeListener = listenerA,
            coroutineContext = dispatcher,
        )

        val listenerB = RecordingNodeListener()
        val nodeB = EpNode(
            identity = identityB,
            nodeListener = listenerB,
            coroutineContext = dispatcher,
        )

        nodeA.addPeer(PeerConfig.Inbound(peerId = "node-b", expectedIdentity = "node-b"))
        nodeB.addPeer(PeerConfig.Outbound(peerId = "node-a", connector = acceptor, expectedIdentity = "node-a"))

        nodeA.start()
        // nodeB has no acceptor; start is a no-op but ensures symmetry with real usage
        nodeB.start()

        assertEquals(1, listenerA.connectedPeers.size, "nodeA should see node-b connected")
        assertEquals("node-b", listenerA.connectedPeers[0].peerId)

        assertEquals(1, listenerB.connectedPeers.size, "nodeB should see node-a connected")
        assertEquals("node-a", listenerB.connectedPeers[0].peerId)

        nodeA.close()
        nodeB.close()
    }
}
