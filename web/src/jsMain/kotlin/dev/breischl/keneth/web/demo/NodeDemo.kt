package dev.breischl.keneth.web.demo

import dev.breischl.keneth.core.messages.Message
import dev.breischl.keneth.core.messages.SessionParameters
import dev.breischl.keneth.core.messages.SoftDisconnect
import dev.breischl.keneth.core.messages.SupplyParameters
import dev.breischl.keneth.core.values.Bounds
import dev.breischl.keneth.core.values.Current
import dev.breischl.keneth.core.values.Power
import dev.breischl.keneth.core.values.Voltage
import kotlin.time.Duration
import dev.breischl.keneth.server.*
import dev.breischl.keneth.transport.CborSnapshot
import dev.breischl.keneth.transport.ReceivedMessage
import dev.breischl.keneth.transport.TransportListener
import kotlinx.browser.document
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLPreElement
import kotlin.js.Date

private lateinit var nodeA: EpNode
private lateinit var nodeB: EpNode
private lateinit var acceptor: InMemoryBidirectionalConnector

/**
 * Initializes the node demo page. Called from [dev.breischl.keneth.web.main].
 *
 * Runs an in-browser network of [EpNode] connected to each other, with logging of the traffic between them.
 * Each node gets its own log box so traffic is easy to distinguish.
 */
fun initNodeDemo() {
    val container = document.getElementById("keneth-demo") as? HTMLElement ?: run {
        console.error("Could not find #keneth-demo container element")
        return
    }

    // --- Intro paragraph ---
    val intro = (document.createElement("div") as HTMLDivElement).apply {
        innerHTML = """
            <p>A minimal demo of two in-memory KENETH nodes communicating. The buttons below control starting and stopping 
            the connection and some messages. This demonstrates connection setup and teardown, connection keepalive pings, 
            code-level message listeners, and message (de-)serialization. No more complex behaviors are shown here. 
            The network is simulated in memory, and the electric parameters are totally fictitious. 
            <br>
            The repeated messages are necessary to prevent connection timeouts.
            </p>
        """.trimIndent()
    }
    container.appendChild(intro)

    // --- Buttons ---
    val connectBtn = (document.createElement("button") as HTMLButtonElement).apply {
        textContent = "Connect"
    }
    val startPublishingBtn = (document.createElement("button") as HTMLButtonElement).apply {
        textContent = "Start Transfer"
        disabled = true
        style.marginLeft = "8px"
    }
    val stopPublishingBtn = (document.createElement("button") as HTMLButtonElement).apply {
        textContent = "Stop Transfer"
        disabled = true
        style.marginLeft = "8px"
    }
    val disconnectBtn = (document.createElement("button") as HTMLButtonElement).apply {
        textContent = "Disconnect"
        disabled = true
        style.marginLeft = "8px"
    }
    val buttonRow = document.createElement("div").apply {
        (this as HTMLElement).style.marginBottom = "8px"
        appendChild(connectBtn)
        appendChild(startPublishingBtn)
        appendChild(stopPublishingBtn)
        appendChild(disconnectBtn)
    }

    // --- Per-node log boxes ---
    fun createLogBox(label: String): Pair<HTMLElement, HTMLPreElement> {
        val pre = (document.createElement("pre") as HTMLPreElement).apply {
            style.apply {
                height = "400px"
                overflowY = "scroll"
                border = "1px solid #ccc"
                padding = "8px"
                fontSize = "13px"
                margin = "0"
            }
        }
        val wrapper = (document.createElement("div") as HTMLElement).apply {
            style.apply {
                flex = "1 1 50%"
                minWidth = "0"
            }
            val heading = document.createElement("h3").apply {
                textContent = label
                (this as HTMLElement).style.margin = "0 0 4px 0"
            }
            appendChild(heading)
            appendChild(pre)
        }
        return wrapper to pre
    }

    val (nodeAWrapper, nodeALog) = createLogBox("battery-node")
    val (nodeBWrapper, nodeBLog) = createLogBox("router-node")

    val logRow = (document.createElement("div") as HTMLElement).apply {
        style.display = "flex"
        style.setProperty("gap", "8px")
        appendChild(nodeAWrapper)
        appendChild(nodeBWrapper)
    }

    container.appendChild(buttonRow)
    container.appendChild(logRow)

    // --- Logging helpers ---
    fun logTo(logElement: HTMLPreElement, msg: String) {
        val timestamp = Date().toISOString().substringAfter("T").substringBefore("Z")
        logElement.textContent += "[$timestamp] $msg\n"
        logElement.scrollTop = logElement.scrollHeight.toDouble()
    }

    fun logBoth(msg: String) {
        logTo(nodeALog, msg)
        logTo(nodeBLog, msg)
    }

    fun listenerFor(logElement: HTMLPreElement) = object : NodeListener {
        override fun onSessionCreated(session: SessionSnapshot) {
            logTo(logElement, "Session created: ${session.sessionId}")
        }

        override fun onSessionActive(session: SessionSnapshot) {
            logTo(logElement, "Session active: sessionId=${session.sessionId} remoteIdentity=${session.remoteIdentity}")
        }

        override fun onPeerConnected(session: SessionSnapshot) {
            logTo(logElement, "Peer connected: ${session.peerId}")
        }

        override fun onSessionDisconnecting(session: SessionSnapshot, softDisconnect: SoftDisconnect?) {
            logTo(logElement, "Disconnecting: ${session.sessionId}")
        }

        override fun onPeerDisconnected(session: SessionSnapshot) {
            logTo(logElement, "Peer disconnected: ${session.peerId}")
        }

        override fun onSessionTimeout(session: SessionSnapshot, timeoutDuration: Duration) {
            logTo(logElement, "Session timed out: ${session.sessionId} after ${timeoutDuration}")
        }

        override fun onSessionClosed(session: SessionSnapshot) {
            logTo(logElement, "Session closed: ${session.sessionId}")
        }
    }

    fun transportListenerFor(logElement: HTMLPreElement) = object : TransportListener {
        override fun onMessageSending(message: Message, payloadCbor: CborSnapshot) {
            logTo(logElement, "Sending: ${message}")
            logTo(logElement, "  CBOR: ${payloadCbor.hex}")
        }

        override fun onMessageReceived(received: ReceivedMessage, payloadCbor: CborSnapshot?) {
            val message = received.message ?: return
            logTo(logElement, "Received: ${message}")
            payloadCbor?.let { logTo(logElement, "  CBOR: ${it.hex}") }
        }
    }

    // --- Connect ---
    connectBtn.addEventListener("click", {
        nodeALog.textContent = ""
        nodeBLog.textContent = ""
        logBoth("Starting nodes...")

        acceptor = InMemoryBidirectionalConnector()

        val nodeAIdentity = "battery-node"
        nodeA = EpNode(
            identity = SessionParameters(identity = nodeAIdentity, type = "battery"),
            acceptor = acceptor,
            transportListener = transportListenerFor(nodeALog),
            nodeListener = listenerFor(nodeALog),
        )

        val nodeBIdentity = "router-node"
        nodeB = EpNode(
            identity = SessionParameters(identity = nodeBIdentity, type = "router"),
            transportListener = transportListenerFor(nodeBLog),
            nodeListener = listenerFor(nodeBLog),
        )

        nodeA.addPeer(PeerConfig.Inbound(nodeBIdentity))
        nodeB.addPeer(PeerConfig.Outbound(nodeAIdentity, connector = acceptor))

        nodeA.start()
        nodeB.start()

        connectBtn.disabled = true
        disconnectBtn.disabled = false
        startPublishingBtn.disabled = false
        logBoth("Nodes started.")
    })

    // --- Start Transfer ---
    startPublishingBtn.addEventListener("click", {
        val paramsProvider: () -> PublishingParams = {
            PublishingParams(
                supply = SupplyParameters(
                    voltage = Voltage(48.0),
                    voltageLimits = Bounds(min = Voltage(48.0), max = Voltage(52.0)),
                    currentLimits = Bounds(min = Current(1.0), max = Current(100.0)),
                    powerLimit = Power(watts = 10000.0)
                )

            )
        }

        val result = nodeA.startPublishing(
            peerId = "router-node",
            paramsProvider = paramsProvider,
        )
        logTo(nodeALog, "StartPublishing result: $result")

        if (result is StartPublishingResult.Success) {
            startPublishingBtn.disabled = true
            stopPublishingBtn.disabled = false
        }
    })

    // --- Stop Transfer ---
    stopPublishingBtn.addEventListener("click", {
        nodeA.stopPublishing("router-node")
        logTo(nodeALog, "Publishing stopped.")
        stopPublishingBtn.disabled = true
        startPublishingBtn.disabled = false
    })

    // --- Disconnect ---
    disconnectBtn.addEventListener("click", {
        logBoth("Disconnecting nodes...")

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            nodeA.close()
            nodeB.close()
            acceptor.close()
            logBoth("All nodes closed.")

            connectBtn.disabled = false
            disconnectBtn.disabled = true
            startPublishingBtn.disabled = true
            stopPublishingBtn.disabled = true
        }
    })
}
