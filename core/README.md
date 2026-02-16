# keneth-core

Core library for the EnergyNet Protocol (EP). Provides typed value classes, message models, CBOR serialization, and
frame encoding/decoding.

## Quickstart

### Value types

EP value types are type-safe inline classes that serialize as single-entry CBOR maps (`{ typeId: value }`).

All numeric values in the library are represented as a `Double`. When deserializing messages any CBOR numeric encoding
is accepted (int, float16, float32, float64), but all values are converted to a `Double`.

Only string-type timestamps are supported, not epoch-based timestamps. This is implied by the EP spec defining a
timestamp as having "Data type: text" in section 3.1.

```kotlin
import dev.breischl.keneth.core.values.*
import net.orandja.obor.codec.Cbor

val cbor = Cbor { ingnoreUnknownKeys = true }

// Encode a Voltage value
val voltage = Voltage(400.0)
val bytes = cbor.encodeToByteArray(VoltageSerializer, voltage)

// Decode from any numeric CBOR encoding (int, float16, float32, float64)
val decoded = cbor.decodeFromByteArray(VoltageSerializer, bytes)
```

Available value types: `Voltage`, `Current`, `Power`, `Energy`, `Percentage`, `Resistance`, `Amount`, `Duration`,
`Text`, `Currency`, `Timestamp`, `Flag`, `Binary`, `SourceMix`, `EnergyMix`, `IsolationState`.

### Frames

Frames are the wire-level unit of communication. `FrameCodec` handles encoding and decoding.

```kotlin
import dev.breischl.keneth.core.frames.*

// Encode
val frame = Frame(headers = emptyMap(), messageTypeId = 0xFFFF_FFFFu, payload = byteArrayOf())
val wireBytes = FrameCodec.encode(frame)

// Decode (returns ParseResult with diagnostics)
val result = FrameCodec.decode(wireBytes)
if (result.succeeded) {
    val decoded = result.value!!
}
```

### Messages

Messages are higher-level protocol objects. Each message type is a subclass of `Message` with its own
CBOR serializer and type ID.

```kotlin
import dev.breischl.keneth.core.messages.*

// Construct messages directly
val ping = Ping
val session = SessionParameters(identity = "vehicle-1", type = "ev")
```

To parse a `Frame` into a `Message`, use the parser classes:

```kotlin
import dev.breischl.keneth.core.parsing.*

val parser = LenientMessageParser()
val result = parser.parseMessage(frame)
if (result.succeeded) {
    when (val msg = result.value!!) {
        is Ping -> println("Got ping")
        is SessionParameters -> println("Identity: ${msg.identity}")
        else -> {}
    }
}
```

For sending and receiving messages over a network connection, see the
[transport module](../transport/README.md) which handles frame encoding/decoding automatically.

Message types: `Ping`, `SessionParameters`, `SoftDisconnect`, `SupplyParameters`, `DemandParameters`,
`StorageParameters`.

## Key packages

| Package       | Description                                                        |
|---------------|--------------------------------------------------------------------|
| `values`      | Typed value classes (Voltage, Current, etc.) with CBOR serializers |
| `messages`    | Message sealed class hierarchy and serializers                     |
| `frames`      | Frame data class and wire-format codec                             |
| `parsing`     | Message registry, lenient/strict parsers                           |
| `diagnostics` | ParseResult, Diagnostic, and severity types                        |

## CBOR serialization

This module uses [OBOR](https://github.com/L-Briand/obor) (`net.orandja.obor:obor`) for CBOR encoding/decoding. OBOR's
`CborObject` provides schema-less access to CBOR data, which lets value-type serializers accept any numeric CBOR
encoding - a key requirement for EP interoperability.
