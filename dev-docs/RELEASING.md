# Releasing KENETH

Releases are published to [Maven Central](https://central.sonatype.com/) via the GitHub Actions
[release workflow](../.github/workflows/release.yml).

## Triggering a release

### Quick Release Via Git Tag

1. Create and push a tag:
   ```bash
   git tag v1.2.3
   git push origin v1.2.3
   ```
2. Wait for the release workflow to finish, then pull `main` to get the snapshot version bump commit.

### Manual Release Action

Go to **Actions → Release → Run workflow** in GitHub, enter the version number (e.g. `1.0.0`,
no `v` prefix), and click **Run workflow**.

The workflow will:

1. Bump `gradle.properties` to the release version, commit, and push a `vX.Y.Z` tag
2. Run all checks
3. Sign and publish artifacts to Maven Central
4. Create a GitHub Release with auto-generated notes
5. Bump `gradle.properties` to the next snapshot (e.g. `1.0.1-SNAPSHOT`) and commit to `main`

> **Note:** When triggering via a tag push instead, step 1 is skipped — the tag itself is the
> source of truth for the version. Steps 2–5 still run.

## One-time Setup

These steps have _already been done_ for the `breischl/KENETh` repo, and should not need to be repeated.
They are retained here as documentation, and in case of setting up a new/different repo.

1. **Maven Central credentials** — Generate a user token at
   [central.sonatype.com](https://central.sonatype.com/) (Account → Generate User Token) and add
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` as GitHub Actions secrets.

2. **GPG signing key** — See `GPG Key Setup Notes.md` in the Obsidian vault for full instructions.
   In short: generate a 4096-bit RSA key, export it as base64, and add `GPG_SIGNING_KEY` and
   `GPG_SIGNING_PASSWORD` as GitHub Actions secrets.


