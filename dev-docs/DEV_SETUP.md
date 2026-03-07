# Developer Setup

## JDK 24+

Required for compilation and tests. Managed automatically via Gradle toolchains — Gradle will
use an existing JDK installation if one is available, or you can install
[Temurin 25](https://adoptium.net/temurin/releases/?version=25) manually.

## Google Chrome

Required for JavaScript browser tests (`jsBrowserTest`, `jsTest`, `allTests`). Node.js-only
tests (`jsNodeTest`) do not need it.

Install the appropriate package for your platform:

| Platform      | Package                                                          |
|---------------|------------------------------------------------------------------|
| Debian/Ubuntu | `sudo apt-get install google-chrome-stable`                      |
| Fedora/RHEL   | `sudo dnf install google-chrome-stable`                          |
| macOS         | Install [Google Chrome](https://www.google.com/chrome/) normally |
| Windows       | Install [Google Chrome](https://www.google.com/chrome/) normally |

If Chrome is installed in a non-standard location, set the `CHROME_BIN` environment variable to
its path before running Gradle:

```bash
export CHROME_BIN=/path/to/google-chrome
./gradlew check
```
