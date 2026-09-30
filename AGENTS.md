# AGENTS.md

Geet (this repo is `geet-android`) is an Android app for downloading music: share a Spotify, YouTube Music or YouTube link to it and the songs download, tagged, with covers and lyrics, into the phone's Music folder. It also plays them. The downloading is done by the [geet](https://github.com/sumdahl/geet) engine, bundled as a native binary. The vocabulary is in `CONTEXT.md` and the decisions are in `docs/adr/`; read both before changing an area.

## The bar

The whole app, every screen and every flow, must feel like a Pixel app: Material 3 Expressive, rich and polished, smooth on 120 Hz screens, with loading, empty, error and offline states designed as carefully as the happy path (ADR 0005). Work in ponytail mode: reuse what the engine, the platform and the libraries already do before writing code.

## Development

The toolchain is JDK 17 (Robolectric tests run on 21 through a Gradle toolchain), Go, the Android SDK at `~/Android/Sdk` (`local.properties`, untracked) and an NDK for the engine. The engine is built from a geet checkout beside this repo (`../geet`), or from `-PgeetEngineDir=…`. CI and releases build the geet commit pinned in `engine.version`; bump it (after the geet PR merges) when the app needs a newer engine.

```bash
./gradlew assembleDebug                                  # build (also builds the engine)
./gradlew installDebug                                   # install on a connected device
./gradlew testDebugUnitTest                              # all unit tests
./gradlew :core:data:testDebugUnitTest --tests '*Lyrics*'   # one test class
./gradlew ktlintFormat                                   # fix formatting
./gradlew recordRoborazziDebug                           # re-record screenshots after an intended UI change
./gradlew lint ktlintCheck detekt testDebugUnitTest verifyRoborazziDebug assembleDebug   # full check (= CI)
```

compileSdk 37, targetSdk 36, minSdk 31 (Android 12), all in `build-logic` (`Config.kt`). Versions live only in `gradle/libs.versions.toml`.

## Architecture

- `:core:engine` runs `libgeet.so` (ADR 0001) with yt-dlp, ffmpeg, ffprobe and QuickJS from youtubedl-android, and decodes its `--json` output (`Models.kt` mirrors geet's docs/03). The engine only ever adds fields, so decoding ignores unknown ones.
- `:core:data` holds settings (DataStore), the download queue (Room plus a WorkManager worker per link, one link at a time), the library (MediaStore) and lyrics.
- `:core:player` holds the one app-wide ExoPlayer, the MediaSession service and the spectrum tap.
- `:core:designsystem` holds the Material 3 Expressive theme, cover-coloured theming and shared components.
- `:ui` holds every screen and its ViewModel.
- `:app` is the shell: navigation, the share sheet activity, the splash screen and the icon.

**Rules that cross modules:**
- **The engine's CLI is the only interface.** Don't reimplement matching, tagging or metadata in Kotlin. When the app needs something new, add it to geet (a command or a JSON field) in a geet PR first.
- Settings that change downloading are engine settings, passed as `GEET_*` variables. The Settings screen is generated from `geet config settings --json`.
- Songs go to the shared `Music/Geet`. That folder only accepts audio files, so the engine's temporary files live in the app's cache (`GEET_WORK_DIR`).
- No streaming (ADR 0002), no accounts or telemetry (ADR 0003).

**Conventions:** each screen has one ViewModel exposing `StateFlow`, collected with `collectAsStateWithLifecycle`. DI is Hilt with KSP. AGP 9 builds Kotlin itself, so never apply `org.jetbrains.kotlin.android` and never use kapt.

## Contributing

**`main` is protected** (ruleset "Protect main"): every change goes in through a pull request. Branch, push the branch, open a PR, and let auto-merge squash it once the required `check` job passes (lint, ktlint, detekt, unit tests, and a debug build with the engine inside). A direct push to `main` is refused, and the PR must be up to date with `main`. Push with gh's token: `git -c credential.helper='!gh auth git-credential' push https://github.com/sumdahl/geet-android.git <branch>`.

## Releasing

Pushing a tag `vX.Y.Z` builds signed per-ABI APKs and publishes a GitHub Release; a tag with a suffix (`v0.1.0-beta.1`) publishes a pre-release. The signing key lives only in repo secrets (`GEET_KEYSTORE_BASE64`, `GEET_KEYSTORE_PASSWORD`, `GEET_KEY_ALIAS`, `GEET_KEY_PASSWORD`); locally an untracked `keystore.properties` does the same, pointing at the key in `~/.config/geet-android/geet-release.jks` (certificate SHA-256 `AD:4C:34:BE:…:3B:12`). **Losing the key means users can't update in place.** Back it up.

## Agent skills

### Issue tracker

Issues and PRDs live in GitHub Issues on sumdahl/geet-android, used through the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
