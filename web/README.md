# keneth-web

Browser-based demos and tools for the EnergyNet Protocol, built with Kotlin/JS.

**Live demos:** [breischl.dev/demos/keneth](https://breischl.dev/demos/keneth/) (once the site is published)

## What's Here

This module compiles Keneth's `core`, `transport`, and `server` libraries to JavaScript via Kotlin/JS and webpack.
The output is a self-contained JS bundle (`web.js`) that runs EP nodes entirely in the browser using in-memory
transports.

### Current Demos

- **Two-Node Demo** — Connects two `EpNode` instances via `InMemoryInboundAcceptor`, performs a session handshake,
  and displays lifecycle events and message details in a scrollable log.

## Development

Start the webpack dev server:

```bash
./gradlew :web:jsBrowserDevelopmentRun
```

This serves the demo at `http://localhost:8080` with hot reload on code changes.

Build the production bundle:

```bash
./gradlew :web:jsBrowserProductionWebpack
```

Output: `web/build/kotlin-webpack/js/productionExecutable/web.js`

## Hugo Site Integration

The production bundle is deployed to the [breischl.dev](https://breischl.dev) Hugo site
(`breischl/breischl.github.io` repo) at `static/demos/keneth/web.js`. Demo pages in that repo use the
`{{</* keneth-demo */>}}` shortcode to load the bundle.

The Kotlin/JS code creates its own DOM elements programmatically — the Hugo side only provides a
`<div id="keneth-demo">` container. This keeps the coupling minimal: the Hugo shortcode is two lines,
and the two repos can be updated independently.

**Deployment:** The production bundle is built and committed to the Hugo repo as part of the
[release workflow](../.github/workflows/release.yml). It runs on release tags, not every `main` push.
See [RELEASING.md](../dev-docs/RELEASING.md) for details.

## Architecture Notes

- This module is **JS-only** — it does not use the shared `kotlin-multiplatform.gradle.kts` convention plugin
  (which adds JVM + linuxArm64 targets). Instead it applies the `kotlin("multiplatform")` plugin directly with
  only the `js(IR)` target.
- Explicit `implementation` dependencies on `:core` and `:transport` are required because Kotlin Multiplatform
  uses `implementation` visibility by default, so transitive dependencies from `:server` aren't exposed.
