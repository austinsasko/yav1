# Contributing to YaV1

Thanks for helping out. YaV1 is GPLv3 open source; contributions are welcome.

## Getting set up

See **[docs/BUILD.md](docs/BUILD.md)** for the toolchain (JDK 17, Android SDK 35) and build
commands. In short:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew :YaV1:assembleDebug
./gradlew :ESPLibrary:testDebugUnitTest :YaV1:testDebugUnitTest
```

## Before you open a PR

- **Build and tests must pass.** CI (`.github/workflows/ci.yml`) runs the debug build and
  both unit-test suites on every PR; run them locally first.
- **Add tests** for new logic. Pure logic (packet parsing, cache math, decision engines)
  should be unit-tested; the existing suites under `YaV1/src/test/` and `ESPLibrary/tests/`
  are the pattern. Network code is tested against recorded fixtures, not live services.
- **Keep new features opt-in** (default off) and fail soft when a data source is
  unavailable — the app must stay a working radar display even when everything else breaks.
- **Match the surrounding style.** This is a legacy Java codebase; keep it Java, keep the
  existing brace/indent conventions in the file you touch.

## Things that will break the build if you forget

- **Do not enable R8 / minification.** ESP callbacks are registered by reflection (method
  name strings) and are silently stripped by the shrinker.
- Leave `android.nonFinalResIds=false` in `gradle.properties` (legacy `switch` on R.id).
- Don't commit `local.properties` (machine-specific SDK path; it's gitignored).

## Commit & PR conventions

- Small, focused commits with a clear subject line; explain the *why* in the body when it's
  not obvious.
- Reference the area in the subject where it helps (`PSL:`, `ADS-B:`, `car:`…).
- Be honest in the PR description about what is and isn't validated — especially anything
  that has only been tested in the emulator vs. against a real Valentine One.

## Reporting issues

Include your Android version, the V1 model and connection type (Gen1 SPP / LE dongle /
Gen2 BLE), and relevant `adb logcat` lines. Useful log tags: `Valentine`, `V1connectionLE`,
`Valentine PSL`, `Valentine POI`, `Valentine ADSB`, `Valentine GEO`, `Valentine TTS`.
