# keneth-transport

Transport layer for the EnergyNet Protocol (EP). Provides frame-level and message-level communication
over TCP and TLS.

## Quickstart

### Sending and receiving messages

Most callers should use `MessageTransport`, which handles frame encoding, CBOR serialization,
and message parsing automatically:

```kotlin
import dev.breischl.keneth.core.messages.*
import dev.breischl.keneth.transport.*
import dev.breischl.keneth.transport.tcp.*

// Create a frame transport and wrap it
val frameTransport = RawTcpClientTransport("charger.local", 56540)
val transport = MessageTransport(frameTransport)

try {
    // Send
    transport.send(SessionParameters(identity = "vehicle-1"))
    transport.send(Ping)

    // Receive
    transport.receive().collect { received ->
        if (received.succeeded) {
            when (val msg = received.message!!) {
                is Ping -> println("Got ping")
                is SessionParameters -> println("Session from: ${msg.identity}")
                else -> println("Got: $msg")
            }
        } else {
            println("Errors: ${received.diagnostics}")
        }
    }
} finally {
    transport.close()
}
```

### TLS

```kotlin
import dev.breischl.keneth.transport.tls.*

val config = TlsConfig(trustStore = myTrustStore)
val frameTransport = TlsClientTransport("charger.example.com", 56540, config)
val transport = MessageTransport(frameTransport)
```

For mTLS (mutual TLS with client certificates):

```kotlin
val config = TlsConfig(
    keyStore = clientKeyStore,
    keyStorePassword = "secret".toCharArray(),
    trustStore = trustStore
)
```

### Server side

```kotlin
import dev.breischl.keneth.transport.tcp.*
import java.net.ServerSocket

val server = ServerSocket(56540)
while (true) {
    val socket = server.accept()
    val frameTransport = RawTcpServerTransport(socket)
    val transport = MessageTransport(frameTransport)
    // handle transport in a coroutine...
}
```

## Architecture

The transport module has two layers:

| Class              | Level     | Description                                          |
|--------------------|-----------|------------------------------------------------------|
| `MessageTransport` | High      | Sends/receives `Message` objects with headers         |
| `FrameTransport`   | Low       | Sends/receives raw `Frame` objects over a connection  |

`MessageTransport` wraps a `FrameTransport` and handles CBOR encoding, frame construction,
and message parsing. Most callers should use `MessageTransport`.

`FrameTransport` is useful when you need raw frame access (forwarding, proxying, custom encoding).

### FrameTransport implementations

| Class                  | Description                                      |
|------------------------|--------------------------------------------------|
| `RawTcpClientTransport`| Creates outbound TCP connections on demand        |
| `RawTcpServerTransport`| Wraps an already-accepted TCP socket              |
| `TlsClientTransport`  | Creates outbound TLS connections                  |
| `TlsServerTransport`  | Wraps an already-accepted TLS socket              |

All socket-based transports extend `SocketTransport`, which handles frame I/O and
wire-format encoding/decoding via `FrameCodec`.

## Debug listener

All transports accept an optional `TransportListener` for observing wire activity
without adding a logging dependency:

```kotlin
val listener = object : TransportListener {
  override fun onConnecting(host: String, port: Int) = println("Connecting to $host:$port")
  override fun onConnected(host: String, port: Int) = println("Connected to $host:$port")
  override fun onMessageSending(message: Message, payloadCbor: CborSnapshot) {
    println("Sending ${message::class.simpleName}: ${payloadCbor.hex}")
    println(payloadCbor.prettyTree)
  }
  override fun onMessageReceived(received: ReceivedMessage, payloadCbor: CborSnapshot?) {
    println("Received: ${received.message}")
  }
}

val frameTransport = RawTcpClientTransport("charger.local", listener = listener)
val transport = MessageTransport(frameTransport, listener = listener)
```

Or use the built-in `PrintTransportListener` for quick stdout logging:

```kotlin
val listener = PrintTransportListener()
val frameTransport = RawTcpClientTransport("charger.local", listener = listener)
val transport = MessageTransport(frameTransport, listener = listener)
```

All listener methods have default no-op implementations — override only what you need.
`CborSnapshot` lazily computes `hex` and `prettyTree`, so there's no cost if unused.
Exceptions thrown by listener methods are silently swallowed.

See [EXAMPLE_OUTPUT.txt](EXAMPLE_OUTPUT.txt) for sample output from `PrintTransportListener`.

## Parsing modes

`MessageTransport` accepts a `MessageParser` to control how received messages are decoded:

- **`LenientMessageParser`** (default): Unknown message types are wrapped in `UnknownMessage`
  with a warning diagnostic. Use for production.
- **`StrictMessageParser`**: Any warning is promoted to an error, causing a parse failure.
  Use for conformance testing.
