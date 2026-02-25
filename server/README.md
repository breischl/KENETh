# keneth-server

Server module for the EnergyNet Protocol (EP). Provides session management, peer tracking,
and energy parameter publishing over TCP connections.

## Quickstart

### High-level API with EpNode

`EpNode` is the main entry point. It manages peer connections and energy transfers:

```kotlin
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.core.values.*
import dev.breischl.keneth.server.*

// Create and start a node
val node = EpNode(
    config = NodeConfig(
        identity = SessionParameters(identity = "router-1", type = "router"),
        listenPort = 56540,
    ),
    listener = object : NodeListener {
        override fun onPeerConnected(peer: PeerSnapshot) {
            println("Peer connected: ${peer.peerId}")
        }
        override fun onPeerDisconnected(peer: PeerSnapshot) {
            println("Peer disconnected: ${peer.peerId}")
        }
        override fun onPeerParametersUpdated(peer: PeerSnapshot, message: Message) {
            println("${peer.peerId} sent ${message::class.simpleName}")
        }
    },
)

// Configure peers we expect to communicate with
node.addPeer(
    PeerConfig(
        peerId = "charger-1",
        direction = PeerDirection.INBOUND,
        expectedIdentity = "charger-1",
    )
)

// Start listening for connections
node.start()

// Start publishing energy parameters to a connected peer
when (val result = node.startTransfer(
    peerId = "charger-1",
    params = TransferParams(
        supply = SupplyParameters(voltage = Voltage(400.0), current = Current(32.0)),
        demand = DemandParameters(voltage = Voltage(400.0)),
    ),
)) {
    is StartTransferResult.Success -> println("Transfer started")
    is StartTransferResult.PeerNotFound -> println("Unknown peer: ${result.peerId}")
    is StartTransferResult.PeerNotConnected -> println("Peer not connected: ${result.state}")
    is StartTransferResult.TransferAlreadyActive -> println("Already transferring")
}

// Update parameters dynamically
node.updateTransfer(
    "charger-1", TransferParams(
        supply = SupplyParameters(voltage = Voltage(800.0), current = Current(16.0)),
    )
)

// Stop transfer and clean up
node.stopTransfer("charger-1")
node.close()
```

## Architecture

The server module is layered:

```
EpNode          — High-level API: peer management + energy transfers
  ├─ EpServer   — Session lifecycle, handshake, message dispatch
  ├─ TcpAcceptor — TCP accept loop (optional, when listenPort is set)
  └─ Transport  — MessageTransport / FrameTransport (from transport module)
```

### Key Classes

| Class            | Description                                                                    |
|------------------|--------------------------------------------------------------------------------|
| `EpNode`         | High-level entry point. Owns an `EpServer` + `TcpAcceptor`.                    |
| `NodeConfig`     | Configuration for an `EpNode` (identity, listen port, transport listener).     |
| `NodeListener`   | Callbacks for peer lifecycle and transfer events.                              |
| `EpServer`       | Low-level session manager. Accepts transports, enforces EP handshake.          |
| `TcpAcceptor`    | Accepts TCP connections and feeds them into an `EpServer`.                     |
| `PeerConfig`     | Configuration for a known peer (ID, host, port, direction, expected identity). |
| `Peer`           | Read-only view of a peer's connection state and latest parameters.             |
| `EnergyTransfer` | Read-only view of an active/stopped parameter transfer.                        |
| `TransferParams` | Supply, demand, and storage parameters to publish.                             |

### Session Lifecycle

Sessions progress through these states:

```
AWAITING_SESSION → ACTIVE → DISCONNECTING → CLOSED
```

1. A transport is accepted via `EpServer.accept()`
2. The device sends `SessionParameters` (handshake)
3. The server replies with its own `SessionParameters`
4. Session becomes `ACTIVE` — messages can be exchanged
5. Either side sends `SoftDisconnect` to begin graceful shutdown

### Peer Management

Peers are named endpoints with a configured direction:

| Direction       | Behavior                                                        |
|-----------------|-----------------------------------------------------------------|
| `INBOUND`       | Wait for the peer to connect to us                              |
| `OUTBOUND`      | We initiate a TCP connection to the peer                        |
| `BIDIRECTIONAL` | We connect outbound, but also accept inbound from this identity |

Inbound connections are matched to configured peers by comparing the remote device's
`SessionParameters.identity` against `PeerConfig.expectedIdentity` (defaults to `peerId`).

### Energy Transfers

`EpNode.startTransfer()` launches a coroutine that publishes parameter messages at a
configurable tick rate (default 100ms). Each non-null field in `TransferParams` is sent
as a separate EP message per tick.

- **`updateTransfer()`** — atomically swaps parameters; next tick uses the new values
- **`stopTransfer()`** — cancels publishing and marks the transfer `STOPPED`
- Transfers auto-stop when the peer disconnects

## Extension Points

For advanced use cases, `EpServer` can be used directly without `EpNode`:

```kotlin
val server = EpServer(
    serverParameters = SessionParameters(identity = "custom-server"),
    listener = object : ServerListener {
        override fun onSessionActive(session: DeviceSessionSnapshot) {
            println("Session active: ${session.sessionParameters?.identity}")
        }
        override fun onMessageReceived(session: DeviceSessionSnapshot, message: Message) {
            println("Received: ${message::class.simpleName}")
        }
    },
)

// Accept transports from any source (TCP, TLS, in-memory fakes for testing)
val transport = MessageTransport(RawTcpServerTransport(socket))
server.accept(transport)
```

`ServerListener` provides lower-level session callbacks than `NodeListener`, including
access to `DeviceSession` objects and handshake failure details.

## Future Work

- Client/implementor provided callbacks in the `accept()` function, to allow them to selectively block connections
- TLS configuration on peers
- Certificate-based inbound authorization
- Auto-reconnect with exponential backoff
- Ping timeout / dead connection detection
- Energy balancing / aggregate coordination module
- Multi-transfer coordination across peers
- Persistent peer configuration
- Policy enforcement (device/LEN/remote levels)