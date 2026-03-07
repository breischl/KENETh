# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build    # Build all targets
./gradlew check    # Run all checks including tests and detekt
./gradlew clean    # Clean build outputs
./gradlew detekt   # Run detekt static analysis only

# Run tests for a specific module (all platforms)
./gradlew :core:allTests
./gradlew :transport:allTests

# Run tests for a specific platform
./gradlew :core:jvmTest
./gradlew :core:jsTest           # Runs Node.js and browser tests
./gradlew :core:jsNodeTest       # Node.js only
./gradlew :core:jsBrowserTest    # Headless Chrome only

# Run a single test class (JVM)
./gradlew :core:jvmTest --tests "dev.breischl.keneth.core.MyTestClass"
```

## Dev Setup

See [DEV_SETUP.md](dev-docs/DEV_SETUP.md) for prerequisites (JDK, Chrome for browser tests).

## Architecture

This is a Kotlin Multiplatform multi-module Gradle project with centralized build configuration.

### Module Structure

- **core/** - EnergyNet Protocol (EP) core library with message models, value types, CBOR serialization, and frame encoding
- **transport/** - Transport abstractions and TCP/TLS implementations for EP
- **server/** - EP server with session management, peer tracking, and energy transfer management
- **buildSrc/** - Convention plugins for shared build logic across modules

### Platform Targets

All modules target three platforms via the `kotlin-multiplatform` convention plugin:

- **JVM** — full implementation; tests run with JUnit Platform
- **JS (IR)** — Node.js and browser (Karma + headless Chrome) test runners
- **linuxArm64** — native binary target

Platform-specific source sets follow the standard KMP layout:

| Source set   | Used for                                       |
|--------------|------------------------------------------------|
| `commonMain` | Platform-agnostic code (most logic lives here) |
| `jvmMain`    | JVM-specific: TCP/TLS sockets                  |
| `jsMain`     | JS-specific stubs and platform actuals         |
| `nativeMain` | Native (linuxArm64) stubs and platform actuals |
| `jvmTest`    | JVM tests (all current tests live here)        |

### Build Configuration

- **JDK 24** (Temurin) via Gradle toolchains
- **Kotlin 2.3.0** with kotlinx ecosystem (coroutines, serialization, datetime)
- Multiplatform convention plugin at `buildSrc/src/main/kotlin/kotlin-multiplatform.gradle.kts` configures all targets,
  JUnit Platform, detekt, and compiler options for all modules
- JVM-only convention plugin at `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` is available for JVM-only modules
- Version catalog at `gradle/libs.versions.toml` manages all dependency versions
- **detekt** 2.0.0-alpha.2 for static analysis, configured at `config/detekt/detekt.yml`
- Build cache and configuration cache enabled in `gradle.properties`

## Making Changes

When making code changes, use red/green TDD (tests first, ensure they fail, then make them pass). Particularly for
bugfixes. Tests should be named in such that the code under test, situation being tested, and expected result are all
visible in the test method name.

In production code, public classes and methods intended as part of the API surface should always documentation comments.
Non-API classes and methods should have comments unless they are trivial. Test classes and methods only require comments
for tricky or complex areas.

Keep the README.md and CLAUDE.md updated as appropriate. 

## Versioning

When starting on a new feature, bump the version in `gradle.properties`. Follow standard SemVer guidelines for the
expected size and type of the change. Development versions should end in `-SNAPSHOT` - the release process will handle
setting non-`SNAPSHOT` versions.