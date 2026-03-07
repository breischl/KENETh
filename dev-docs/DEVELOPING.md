# Developing KENETH

## Initial Setup

See [DEV_SETUP.md](DEV_SETUP.md)

## Build

This project uses [Gradle](https://gradle.org/).

* Run `./gradlew build` to build all targets.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.
* Run `./gradlew :core:jvmTest` (or `:transport:jvmTest`, `:server:jvmTest`) for JVM tests on a specific module.
* Run `./gradlew :core:allTests` to run tests across all platforms for a module.

See [CLAUDE.md](../CLAUDE.md) for full build command reference.

## Versioning

Development versions in `gradle.properties` should always end with `-SNAPSHOT`.

## Releasing

See [RELEASING.md](RELEASING.md)

## Useful References

- [EnergyNet Protocol spec](https://github.com/energyetf/energynet)
- [CBOR spec](https://cbor.io/)
- [cbor.me](https://cbor.me/) is a handy CBOR debugging tool