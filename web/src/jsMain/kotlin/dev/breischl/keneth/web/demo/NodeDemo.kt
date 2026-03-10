package dev.breischl.keneth.web.demo

import dev.breischl.keneth.core.messages.DemandParameters
import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.Ping
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.SoftDisconnect
import dev.breischl.keneth.core.messages.StorageParameters
import dev.breischl.keneth.core.messages.SupplyParameters
import dev.breischl.keneth.core.messages.UnknownMessage
import dev.breischl.keneth.server.EpNode
import dev.breischl.keneth.server.InMemoryInboundAcceptor
import dev.breischl.keneth.server.NodeListener
import dev.breischl.keneth.server.PeerConfig
import dev.breischl.keneth.server.SessionSnapshot
import dev.breischl.keneth.transport.CborSnapshot
import dev.breischl.keneth.transport.ReceivedMessage
import dev.breischl.keneth.transport.TransportListener
import kotlinx.browser.document
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLPreElement
import kotlin.js.Date

private lateinit var nodeA: EpNode
private lateinit var nodeB: EpNode
private lateinit var acceptor: InMemoryInboundAcceptor

/**
 * Runs an in-browser network of [EpNode] connected to each other, with logging of the traffic between them.
 */
fun main() {
    val container = document.getElementById("keneth-demo") as? HTMLElement ?: run {
        console.error("Could not find #keneth-demo container element")
        return
    }

    // Build UI elements programmatically
    val runBtn = (document.createElement("button") as HTMLButtonElement).apply {
        textContent = "Run"
    }
    val disconnectBtn = (document.createElement("button") as HTMLButtonElement).apply {
        textContent = "Disconnect"
        disabled = true
        style.marginLeft = "8px"
    }
    val buttonRow = document.createElement("div").apply {
        (this as HTMLElement).style.marginBottom = "8px"
        appendChild(runBtn)
        appendChild(disconnectBtn)
    }
    val logElement = (document.createElement("pre") as HTMLPreElement).apply {
        style.apply {
            height = "80%"
            minHeight = "400px"
            overflowY = "scroll"
            border = "1px solid #ccc"
            padding = "8px"
            fontSize = "13px"
        }
    }

    container.appendChild(buttonRow)
    container.appendChild(logElement)

    fun log(label: String, msg: String) {
        val timestamp = Date().toISOString().substringAfter("T").substringBefore("Z")
        logElement.textContent += "[$timestamp] [$label] $msg\n"
        logElement.scrollTop = logElement.scrollHeight.toDouble()
    }

    fun listenerFor(label: String) = object : NodeListener {
        override fun onSessionCreated(session: SessionSnapshot) {
            log(label, "Session created: ${session.sessionId}")
        }

        override fun onSessionActive(session: SessionSnapshot) {
            log(label, "Session active: ${session.sessionId}")
        }

        override fun onPeerConnected(session: SessionSnapshot) {
            log(label, "Peer connected: ${session.peerId}")
        }

        override fun onSessionDisconnecting(session: SessionSnapshot, softDisconnect: SoftDisconnect?) {
            log(label, "Disconnecting: ${session.sessionId}")
        }

        override fun onPeerDisconnected(session: SessionSnapshot) {
            log(label, "Peer disconnected: ${session.peerId}")
        }

        override fun onSessionClosed(session: SessionSnapshot) {
            log(label, "Session closed: ${session.sessionId}")
        }
    }

    fun transportListenerFor(label: String) = object : TransportListener {
        override fun onMessageSending(message: Message, payloadCbor: CborSnapshot) {
            log(label, "Sending: ${message}")
            log(label, "  CBOR: ${payloadCbor.hex}")
        }

        override fun onMessageReceived(received: ReceivedMessage, payloadCbor: CborSnapshot?) {
            val message = received.message ?: return
            log(label, "Received: ${message}")
            payloadCbor?.let { log(label, "  CBOR: ${it.hex}") }
        }
    }

    runBtn.addEventListener("click", {
        logElement.textContent = ""
        log("demo", "Starting nodes...")

        acceptor = InMemoryInboundAcceptor()

        nodeA = EpNode(
            identity = SessionParameters(identity = "node-a", type = "charger"),
            acceptor = acceptor,
            transportListener = transportListenerFor("node-a"),
            nodeListener = listenerFor("node-a"),
        )

        nodeB = EpNode(
            identity = SessionParameters(identity = "node-b", type = "router"),
            transportListener = transportListenerFor("node-b"),
            nodeListener = listenerFor("node-b"),
        )

        nodeA.addPeer(PeerConfig.Inbound("node-b"))
        nodeB.addPeer(PeerConfig.Outbound("node-a", connector = acceptor))

        nodeA.start()
        nodeB.start()

        runBtn.disabled = true
        disconnectBtn.disabled = false
        log("demo", "Nodes started.")
    })

    disconnectBtn.addEventListener("click", {
        log("demo", "Disconnecting nodes...")

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            nodeA.close()
            nodeB.close()
            acceptor.close()
            log("demo", "All nodes closed.")

            runBtn.disabled = false
            disconnectBtn.disabled = true
        }
    })
}
