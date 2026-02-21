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

#### [keneth-core](core/README.md)
Message models, value types, CBOR serialization, frame encoding/decoding.

#### [keneth-transport](transport/README.md)
Transport abstractions and TCP/TLS implementations. BLE deferred but interface accommodates it.

#### keneth-server

EP server with session management, TCP accept loop, and peer configuration. Manages device connections,
enforces the EP handshake protocol, and tracks per-device state. Supports named peers with inbound matching
by identity and outbound connection initiation.

### Build
This project uses [Gradle](https://gradle.org/).

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.