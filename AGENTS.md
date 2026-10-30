# PDF Reader maintenance

- One Android app module: Kotlin / Compose, minSdk 31, compile/target 36. Keep applicationId and release signing identity compatible with existing installs.
- Source local build environment with `source scripts/env.sh`; use the committed Gradle Wrapper. Tools/caches live in ignored `.tools`; secrets in ignored `.secrets`. Never print secret environment files or commit them.
- Quick checks: `./gradlew testDebugUnitTest`; final: `./scripts/release.sh` (unit tests, debug/release lint, debug/test/release APKs, signature/checksum).
- Device checks require a fully booted test Android. `bash scripts/test_emulator.sh emulator-5554`. The test APK and target must have compatible signatures. Black-box release/upgrade checks use `scripts/smoke_release.py`; see `docs/RELEASE-v0.2.0.md`. Compilation does not substitute for runtime results.
- `MainActivity` routes incoming PDF intents. `PdfIntents` validates the local input. `DocumentAccess` handles provider metadata and URI grants. `ReaderViewModel` owns document lifecycle/history; `PdfEngine` serializes platform rendering off the main thread. UI requests lazy page rendering.
- Keep changes scoped, preserve unrelated work, use `codex/` branches. Avoid blanket keep rules, full-icon libraries, cache cleans or reinstallations without measured need.
- Record actual current outcomes and limitations in `docs/VALIDATION.md` and version-specific release notes. Never carry a historical pass forward as proof for changed code.
