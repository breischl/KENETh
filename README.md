# KENETh: Kotlin EnergyNET Protocol Library

[![CI](https://github.com/breischl/KENETh/actions/workflows/ci.yml/badge.svg)](https://github.com/breischl/KENETh/actions/workflows/ci.yml)

## Overview

A Kotlin library for working with the EnergyNet Protocol (EP). Includes serialization and transport code, with plans
to add a higher-level server implementation.

## Development

### Useful References And Tools

- [EnergyNet Protocol spec](https://github.com/energyetf/energynet)
- [CBOR spec](https://cbor.io/)
- [cbor.me](https://cbor.me/) is a handy CBOR debugging tool

### Module Structure

All modules target JVM, JS (IR), and linuxArm64 via Kotlin Multiplatform. Platform-agnostic code lives in
`commonMain`; platform-specific implementations (e.g. TCP sockets on JVM) live in their respective source sets.

#### [keneth-core](core/README.md)

Message models, value types, CBOR serialization, frame encoding/decoding. Fully cross-platform.

#### [keneth-transport](transport/README.md)

Transport abstractions and TCP/TLS implementations. Core interfaces are cross-platform; TCP/TLS socket
implementations are JVM-only (`jvmMain`). BLE deferred but interface accommodates it.

#### [keneth-server](server/README.md)
EP server with session management, TCP accept loop, and peer configuration. Manages device connections,
enforces the EP handshake protocol, and tracks per-device state. Supports named peers with inbound matching
by identity and outbound connection initiation. Server logic is cross-platform; TCP accept loop is JVM-only.

### Build
This project uses [Gradle](https://gradle.org/).

* Run `./gradlew build` to build all targets.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.
* Run `./gradlew :core:jvmTest` (or `:transport:jvmTest`, `:server:jvmTest`) for JVM tests on a specific module.
* Run `./gradlew :core:allTests` to run tests across all platforms for a module.

See [CLAUDE.md](CLAUDE.md) for full build command reference.