# keneth-server

Server module for the EnergyNet Protocol (EP). Provides session management, peer tracking,
and energy parameter publishing over TCP connections.

## Quickstart

`EpNode` is the main entry point. It manages peer connections and energy transfers:

```kotlin
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.values.*
import dev.breischl.keneth.server.*
import dev.breischl.keneth.server.TcpAcceptor

// Create and start a node
val node = EpNode(
    identity = SessionParameters(identity = "router-1", type = "router"),
    acceptor = TcpAcceptor(port = 56540),
    nodeListener = object : NodeListener {
        override fun onPeerConnected(session: SessionSnapshot) {
            println("Peer connected: ${session.peerId}")
        }
        override fun onPeerDisconnected(session: SessionSnapshot) {
            println("Peer disconnected: ${session.peerId}")
        }
        override fun onMessageReceived(session: SessionSnapshot, message: Message) {
            println("${session.peerId} sent ${message::class.simpleName}")
        }
    }
)

// Configure peers we expect to communicate with
node.addPeer(PeerConfig.Inbound(peerId = "charger-1"))

// Start listening for connections
node.start()

// Start publishing transfer parameters to a connected peer
var supply = SupplyParameters(voltage = Voltage(400.0), current = Current(32.0))
when (val result = node.startTransfer(
    peerId = "charger-1",
    paramsProvider = {
        TransferParams(
            supply = supply,
            demand = DemandParameters(voltage = Voltage(400.0)),
        )
    },
)) {
    is StartTransferResult.Success -> println("Transfer started")
    is StartTransferResult.PeerNotFound -> println("Unknown peer: ${result.peerId}")
    is StartTransferResult.PeerNotConnected -> println("Peer not connected: ${result.peerId}")
    is StartTransferResult.TransferAlreadyActive -> println("Already transferring")
}

// Update parameters dynamically — paramsProvider is called each tick, so just update the captured state
supply = SupplyParameters(voltage = Voltage(800.0), current = Current(16.0))

// Stop transfer and clean up
node.stopTransfer("charger-1")
node.close()
```

## Key Classes

| Class                     | Description                                                                        |
|---------------------------|------------------------------------------------------------------------------------|
| `EpNode`                  | Main entry point. Accepts connections, enforces EP handshake, dispatches messages. |
| `NodeListener`            | Callbacks for session and peer lifecycle events.                                   |
| `SessionSnapshot`         | Immutable snapshot of a session's state at a point in time.                        |
| `PeerConfig`              | Configuration for a known peer — `Inbound` or `Outbound`.                          |
| `Peer`                    | Read-only view of a peer's current connection state.                               |
| `EnergyTransfer`          | Read-only view of an active/stopped parameter transfer.                            |
| `TransferParams`          | Supply, demand, and storage parameters to publish.                                 |
| `InboundAcceptor`         | Interface for accepting inbound connections from other EP nodes                    |
| `TcpAcceptor`             | Accepts TCP connections and feeds them into an `EpNode` (JVM-only).                |
| `InMemoryInboundAcceptor` | In-process acceptor for testing without real sockets.                              |

## Session Lifecycle

Sessions progress through these states:

```
AWAITING_SESSION → ACTIVE → DISCONNECTING → CLOSED
```

1. A transport is accepted (via `TcpAcceptor` or `EpNode.accept()` directly)
2. The device sends `SessionParameters` (handshake)
3. The node replies with its own `SessionParameters`
4. Session becomes `ACTIVE` — messages can be exchanged
5. Either side sends `SoftDisconnect` to begin graceful shutdown

## Peer Management

Peers are named endpoints configured before or after the node starts:

| Config type                                 | Behavior                                                        |
|---------------------------------------------|-----------------------------------------------------------------|
| `PeerConfig.Inbound`                        | Wait for the peer to connect to us                              |
| `PeerConfig.Outbound`                       | We initiate a connection to the peer via a `PeerConnector`      |
| `PeerConfig.Outbound(acceptInbound = true)` | We connect outbound, but also accept inbound from this identity |

Inbound connections are matched to configured peers by comparing the remote device's
`SessionParameters.identity` against `PeerConfig.expectedIdentity` (defaults to `peerId`).

## Energy Transfers

`EpNode.startTransfer()` launches a coroutine that publishes parameter messages at a
configurable tick rate (default 100ms). Each non-null field in `TransferParams` is sent
as a separate EP message per tick.

- **`stopTransfer()`** — cancels publishing and marks the transfer `STOPPED`
- Transfers auto-stop when the peer disconnects

## Platform support

`keneth-server` is a Kotlin Multiplatform library targeting JVM, JS (IR), and linuxArm64.

Server logic lives in `commonMain`. Platform-specific code provides the TCP accept loop:

| Source set   | Contents                                              |
|--------------|-------------------------------------------------------|
| `commonMain` | `EpNode`, `NodeListener`, session/peer/transfer logic |
| `jvmMain`    | `TcpAcceptor`                                         |
| `jsMain`     | JS stubs                                              |
| `nativeMain` | Native stubs                                          |

## Future Work

- Client/implementor provided callbacks in the `accept()` function, to allow them to selectively block connections
- TLS configuration on peers
- Certificate-based inbound authorization
- Auto-reconnect with exponential backoff