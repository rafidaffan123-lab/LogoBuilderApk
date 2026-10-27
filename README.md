# LogoBuilder Android (clean-room) — v0.2

Native Kotlin / Jetpack Compose / Material 3 app for **inspecting** boot-logo files. Independent of the
Windows LogoBuilder; no code or binaries from it are used.

## Status (honest)
| Area | State |
|---|---|
| Import via Storage Access Framework, bounded read (64 MB), off-main-thread | implemented |
| SHA-256, generic signatures, offset-aware hex viewer with jump | implemented |
| Recent files, progress, cancel, error/warning/info banners | implemented |
| Unchanged-copy export + ZIP export, write-then-verify by hash, never overwrites source | implemented |
| Replacement image import, dimension validation, preview | implemented |
| Format framework (`LogoFormat`, registry, safe reader/math) | implemented |
| **logo.bin parser / writer** | **NOT implemented — no verified format** |
| Extracted-image list, rebuild with replacement | blocked (needs parser) |

**Supported logo formats: none yet.** Unknown files are reported as unsupported; nothing is faked.
The MediaTek header magic is shown only as an *unverified hint*.

## Not verified in the authoring environment
The environment had a JRE only (no JDK/Kotlin compiler/Gradle/Android SDK, no network), so
**the project has not been compiled, the unit tests have not been run, and no APK was built.**
Test expectations (SHA vectors, hex row format, RGB565 values) were cross-checked with an independent
Python model, which is not a substitute for running the Kotlin tests.

## Build
1. Android Studio (Koala or newer, JDK 17) → open this folder → Gradle sync.
2. Tests: `./gradlew :app:testDebugUnitTest` (use `gradle wrapper --gradle-version 8.7` once if
   `gradlew` is missing; only `gradle-wrapper.properties` is shipped because the jar could not be generated).
3. APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
4. Release: supply your own signing config; `isMinifyEnabled = true` is already set.

## Architecture
`core/` pure Kotlin (SafeMath, ByteReader, Hashing, HexDump, Signatures, BoundedReader, Inspector) ·
`format/` LogoFormat, FormatRegistry (empty by default) · `image/` ImageRules (pure), ImageDecoder (Android) ·
`export/` ZipExporter, ExportNames · `state/` UiState + pure transitions · `data/` FileRepository, RecentStore/Codec ·
`ui/` MainViewModel (coroutines, single-flight ops), Compose screens.

## Fixes versus the v0.1 MVP
stale state after failed import · `lastPathSegment` names → `OpenableColumns` · hashing/IO on main thread ·
unbounded `readBytes` · success shown after failed export · duplicate taps · no loading/cancel · weak signatures ·
no tests · no lifecycle-aware state · missing wrapper config.

## Adding a format
Collect samples → fill `format-spec/notes.md` (provenance, header, endianness, offsets, pixel format, padding) →
implement `LogoFormat` with `SafeMath`/`ByteReader` → test against independent expected outputs
(not parser↔writer self round-trip) → register in `FormatRegistry.default`.
