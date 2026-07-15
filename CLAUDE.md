# YaV1 — instructions for Claude Code sessions

## Branch workflow (IMPORTANT)
- **Always branch from up-to-date `origin/master`, and target every PR at `master`.** Never base work on another feature branch, never merge feature branches into each other, and never stack PRs.
- Why: PRs #2–#5 were squash-merged from stacked branches, which repeatedly produced phantom merge conflicts (identical reviewed content under divergent histories). Master is the only integration branch.

## Build & test
- Build (JDK 17): `JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home ./gradlew :YaV1:assembleDebug` — APK at `YaV1/build/outputs/apk/debug/`.
- Tests: `./gradlew :ESPLibrary:testDebugUnitTest :YaV1:testDebugUnitTest` (ESPLibrary tests live in the non-standard `ESPLibrary/tests/` srcDir).
- Keep `minifyEnabled false` — ESP callbacks are registered via string reflection (`registerForAlertData(obj, "method")`) and break under R8.
- `gradle.properties` must keep `android.nonFinalResIds=false` (code switches on `R.id`).
- `local.properties` is untracked; Android SDK at `~/Library/Android/sdk`.

## Conventions
- Java, not Kotlin — match the existing code style.
- Emulators on this machine: `yav1_test` (API 33 arm64) and `yav1_api35` (Android 15 arm64). ValentineESP demo mode replays alerts with no V1 hardware or Bluetooth.
- The Android Auto car app must never claim navigation focus (no `NavigationManager.navigationStarted()`, no `Trip`/TBT updates, no cluster APIs) — it complements Google Maps/Waze, never replaces them.
