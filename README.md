# KENETH: Kotlin EnergyNET Host

[![CI](https://github.com/breischl/KENETh/actions/workflows/ci.yml/badge.svg)](https://github.com/breischl/KENETh/actions/workflows/ci.yml)
![Maven Central Version](https://img.shields.io/maven-central/v/dev.breischl.keneth/server?strategy=highestVersion)

## Overview

A Kotlin Multiplatform library for implementing the [EnergyNet Protocol (EP)](https://github.com/energyetf/energynet) —
an open protocol for energy transfer coordination between devices such as EVs, chargers, and energy routers.

KENETH provides:

- **Server-side session management** with EP handshake enforcement, peer tracking, and energy parameter publishing
- **Frame encoding/decoding** for the EP wire format
- **CBOR serialization** of all EP message types and value types
- **TCP and TLS transport** for sending and receiving EP messages

## Modules

KENETH provides modules at several levels of abstraction. You can use whichever is appropriate for your project.
From the most low-level, transport adjacent to the most high-level/abstracted, they are:

### [keneth-server](server/README.md)

Server classes that manage incoming & outgoing peer connections, and message publishing. `EpNode` provides the "brain
stem"
for an EnergyNet node. This is the highest-level abstraction, intended for building EnergyNet applications.

### [keneth-transport](transport/README.md)

Transport layer: `MessageTransport` and `FrameTransport` interfaces with TCP and TLS implementations. Handles frame
encoding/decoding and message parsing automatically.

### [keneth-core](core/README.md)

Core EP library: typed value classes (`Voltage`, `Current`, `Power`, etc.), message models, CBOR serialization, and
frame encoding/decoding. Useful for testing, debugging, and as a building block.

## Platform Support

All modules target JVM, JS (IR), and linuxArm64 via Kotlin Multiplatform. Platform-agnostic code lives in `commonMain`;
platform-specific implementations (TCP/TLS sockets, TCP accept loop) are in `jvmMain`.

## Development

See [dev-docs/DEVELOPING.md](dev-docs/DEVELOPING.md) for setup and practices. See [CLAUDE.md](CLAUDE.md) for agent
instructions.
