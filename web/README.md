# keneth-web

Browser-based demos and tools for the EnergyNet Protocol, built with Kotlin/JS.

**Live demos:** [breischl.dev/demos/keneth](https://breischl.dev/demos/keneth/) (once the site is published)

## What's Here

This module compiles Keneth's `core`, `transport`, and `server` libraries to JavaScript via Kotlin/JS and webpack.
The output is a self-contained JS bundle (`web.js`) that runs EP nodes entirely in the browser using in-memory
transports.

### Pages

- **index.html** - Index and links to other demos.
- **Two-Node Demo** (`two_node.html`) — Connects two `EpNode` instances via `InMemoryBidirectionalConnector`, performs a
  session handshake, and displays lifecycle events and message details in a scrollable log.
- **Message Debugger** (`message_debugger.html`) — Decode hex-encoded EP frames to human-readable text, or encode text
  back to
  hex. Supports all EP message types. Useful for inspecting wire-format captures or crafting test messages.

## Development

To achieve continuous hot-reload on web changes, you need to use two terminals.

```bash
# Terminal one
./gradlew :web:jsBrowserDevelopmentRun

#Terminal two
./gradlew :web:compileDevelopmentExecutableKotlinJs --continuous
```


To build the production bundle (e.g., to do a local deployment to the static Hugo site):
```bash
./gradlew :web:jsBrowserProductionWebpack
```
Output: `web/build/kotlin-webpack/js/productionExecutable/web.js`

## Hugo Site Integration

The production bundle is deployed to the [breischl.dev](https://breischl.dev) Hugo site
(`breischl/breischl.github.io` repo) at `static/demos/keneth/web.js`. Demo pages in that repo use the
`{{< keneth-demo >}}` shortcode to load the bundle.

The Kotlin/JS code creates its own DOM elements programmatically — the Hugo side only provides a
container `<div>` with the appropriate ID (`keneth-demo` or `keneth-message-debugger`). This keeps the coupling
minimal: the Hugo shortcode is two lines, and the two repos can be updated independently.

**Deployment:** The production bundle is built and committed to the Hugo repo as part of the
[release workflow](../.github/workflows/release.yml). It runs on release tags, not every `main` push.
See [RELEASING.md](../dev-docs/RELEASING.md) for details.

## Architecture Notes

- This module is **JS-only** — it does not use the shared `kotlin-multiplatform.gradle.kts` convention plugin
  (which adds JVM + linuxArm64 targets). Instead it applies the `kotlin("multiplatform")` plugin directly with
  only the `js(IR)` target.
- Explicit `implementation` dependencies on `:core` and `:transport` are required because Kotlin Multiplatform
  uses `implementation` visibility by default, so transitive dependencies from `:server` aren't exposed.
- A single `main()` in `Main.kt` dispatches to the appropriate page initializer based on which container
  element ID is present in the DOM (`#keneth-demo` → node demo, `#keneth-message-debugger` → message debugger).
