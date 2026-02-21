# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew run      # Build and run the application
./gradlew build    # Build only
./gradlew check    # Run all checks including tests
./gradlew clean    # Clean build outputs

# Run tests for a specific module
./gradlew :core:test
./gradlew :transport:test

# Run a single test class
./gradlew :app:test --tests "com.example.MyTestClass"
```

## Architecture

This is a Kotlin/JVM multi-module Gradle project with centralized build configuration.

### Module Structure

- **core/** - EnergyNet Protocol (EP) core library with message models, value types, CBOR serialization, and frame encoding
- **transport/** - Transport abstractions and TCP/TLS implementations for EP
- **buildSrc/** - Convention plugin for shared build logic across modules

### Build Configuration

- **JDK 25** (Temurin) via Gradle toolchains
- **Kotlin 2.3.0** with kotlinx ecosystem (coroutines, serialization, datetime)
- Convention plugin at `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` configures JVM toolchain and JUnit Platform for all modules
- Version catalog at `gradle/libs.versions.toml` manages all dependency versions
- Build cache and configuration cache enabled in `gradle.properties`

## Making Changes

When making code changes, use red/green TDD (tests first, ensure they fail, then make them pass). Particularly for
bugfixes. Tests should be named in such that the code under test, situation being tested, and expected result are all
visible in the test method name.

In production code, public classes and methods intended as part of the API surface should always documentation comments.
Non-API classes and methods should have comments unless they are trivial. Test classes and methods only require comments
for tricky or complex areas.

Keep the README.md and CLAUDE.md updated as appropriate. 